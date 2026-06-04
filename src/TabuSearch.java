import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.concurrent.ThreadLocalRandom;

public class TabuSearch {
    private static final int OP_RELOCATE = 0;
    private static final int OP_ADD = 1;
    private static final int OP_REMOVE_ADD_CHAIN = 2;
    private static final int OP_WAITING_REDUCE = 3;

    private final DistanceMatrix dm;
    private final City[] cities;
    private final int totalCities;

    public TabuSearch(DistanceMatrix dm, City[] cities) {
        this.dm = dm;
        this.cities = cities;
        this.totalCities = cities.length;
    }

    public Tour improve(Tour tour, int maxIter, int tabuTenure) {
        Tour best = tour.copy(cities, dm);
        Tour current = tour.copy(cities, dm);
        ArrayDeque<String> tabuList = new ArrayDeque<>();
        ThreadLocalRandom rand = ThreadLocalRandom.current();

        if (current.visitedCount < 1) {
            return best;
        }

        boolean largeInstance = totalCities > 5000;

        // Tabu iteration loop
        for (int iter = 0; iter < maxIter; iter++) {
            int op = pickOperator(rand, largeInstance);
            MoveResult move = applyOperator(current, op, rand, largeInstance);
            if (move == null) {
                continue;
            }

            boolean tabu = tabuList.contains(move.key);
            boolean improvesCurrent = Tour.isBetter(move.tour, current);
            boolean improvesBest = Tour.isBetter(move.tour, best);

            if (tabu && !improvesBest) {
                continue;
            }
            if (!tabu && !improvesCurrent && !improvesBest) {
                continue;
            }

            current = move.tour;
            if (improvesBest) {
                best = move.tour.copy(cities, dm);
            }
            tabuList.add(move.key);
            if (tabuList.size() > tabuTenure) {
                tabuList.poll();
            }
        }
        return best;
    }

    private int pickOperator(ThreadLocalRandom rand, boolean large) {
        int r = rand.nextInt(100);
        if (large) {
            if (r < 40) return OP_ADD;
            if (r < 70) return OP_REMOVE_ADD_CHAIN;
            if (r < 92) return OP_WAITING_REDUCE;
            return OP_RELOCATE;
        }
        if (r < 35) return OP_ADD;
        if (r < 60) return OP_REMOVE_ADD_CHAIN;
        if (r < 85) return OP_WAITING_REDUCE;
        return OP_RELOCATE;
    }

    private MoveResult applyOperator(Tour current, int op, ThreadLocalRandom rand, boolean large) {
        return switch (op) {
            case OP_ADD -> tryAdd(current, rand, large);
            case OP_REMOVE_ADD_CHAIN -> tryRemoveAddChain(current, rand, large);
            case OP_WAITING_REDUCE -> tryWaitingReduce(current, rand);
            default -> tryRelocate(current, rand);
        };
    }

    // Insert out-of-tour city
    private MoveResult tryAdd(Tour current, ThreadLocalRandom rand, boolean large) {
        boolean[] inTour = inTourMask(current.cities);
        int[] outCandidates = sampleOutOfTour(inTour, current.cities, rand, large ? 120 : 40);
        if (outCandidates.length == 0) {
            return null;
        }

        Tour best = null;
        String bestKey = null;
        for (int city : outCandidates) {
            int[] positions = candidateInsertPositions(current.cities, city, large);
            for (int pos : positions) {
                int[] trial = insertAt(current.cities, city, pos);
                if (!Tour.isValid(trial, cities, dm)) {
                    continue;
                }
                Tour t = new Tour(trial, cities, dm);
                if (best == null || Tour.isBetter(t, best)) {
                    best = t;
                    bestKey = "ADD:" + city + "@" + pos;
                }
            }
        }
        if (best == null) {
            return null;
        }
        return new MoveResult(best, bestKey);
    }

    // Remove then greedy re-insert
    private MoveResult tryRemoveAddChain(Tour current, ThreadLocalRandom rand, boolean large) {
        int n = current.visitedCount;
        if (n < 15) {
            return null;
        }

        int removeCount = 5 + rand.nextInt(6);
        if (removeCount >= n - 2) {
            removeCount = Math.max(2, n / 4);
        }
        int[] removeIdx = distinctIndices(n, removeCount, rand);
        int[] route = removeIndices(current.cities, removeIdx);

        boolean[] inTour = inTourMask(route);
        int addAttempts = 10 + rand.nextInt(11);

        Tour best = null;
        String bestKey = null;

        for (int step = 0; step < addAttempts; step++) {
            int[] outCandidates = sampleOutOfTour(inTour, route, rand, large ? 120 : 35);
            if (outCandidates.length == 0) {
                break;
            }

            Tour stepBest = null;
            int stepCity = -1;
            int stepPos = -1;

            for (int city : outCandidates) {
                int[] positions = candidateInsertPositions(route, city, large);
                for (int pos : positions) {
                    int[] trial = insertAt(route, city, pos);
                    if (!Tour.isValid(trial, cities, dm)) {
                        continue;
                    }
                    Tour t = new Tour(trial, cities, dm);
                    if (stepBest == null || Tour.isBetter(t, stepBest)) {
                        stepBest = t;
                        stepCity = city;
                        stepPos = pos;
                    }
                }
            }

            if (stepBest == null) {
                break;
            }

            route = stepBest.cities;
            inTour[stepCity] = true;

            if (best == null || Tour.isBetter(stepBest, best)) {
                best = stepBest;
                bestKey = "RAC:" + Arrays.toString(removeIdx) + "+" + stepCity + "@" + stepPos;
            }
        }

        if (best == null || best.visitedCount <= current.visitedCount) {
            if (best != null && best.visitedCount == current.visitedCount
                    && Tour.isBetter(best, current)) {
                return new MoveResult(best, bestKey);
            }
            return null;
        }
        return new MoveResult(best, bestKey != null ? bestKey : "RAC");
    }

    // Reduce early waiting time
    private MoveResult tryWaitingReduce(Tour current, ThreadLocalRandom rand) {
        int n = current.visitedCount;
        int[] waitingIdx = findWaitingIndices(current.cities);
        if (waitingIdx.length == 0) {
            return tryRelocate(current, rand);
        }

        int idx = waitingIdx[rand.nextInt(waitingIdx.length)];
        int city = current.cities[idx];

        Tour best = null;
        String bestKey = null;

        for (int attempt = 0; attempt < 12; attempt++) {
            int newPos;
            if (attempt < 6) {
                int minLater = idx + 1;
                if (minLater >= n) {
                    continue;
                }
                newPos = minLater + rand.nextInt(n - minLater);
            } else {
                newPos = rand.nextInt(n + 1);
            }

            int[] trial = relocateCity(current.cities, idx, newPos);
            if (!Tour.isValid(trial, cities, dm)) {
                continue;
            }
            Tour t = new Tour(trial, cities, dm);
            if (best == null || Tour.isBetter(t, best)) {
                best = t;
                bestKey = "WAIT:" + city + ":" + idx + "->" + newPos;
            }
        }

        if (best == null) {
            return null;
        }
        return new MoveResult(best, bestKey);
    }

    private MoveResult tryRelocate(Tour current, ThreadLocalRandom rand) {
        int n = current.visitedCount;
        if (n < 2) {
            return null;
        }
        int a = rand.nextInt(n);
        int b = rand.nextInt(n);
        if (a == b) {
            return null;
        }
        int[] trial = relocateCity(current.cities, a, b);
        if (!Tour.isValid(trial, cities, dm)) {
            return null;
        }
        Tour t = new Tour(trial, cities, dm);
        return new MoveResult(t, "REL:" + a + "," + b);
    }

    private int[] findWaitingIndices(int[] route) {
        int[] buf = new int[route.length];
        int count = 0;
        int time = 0;
        for (int i = 0; i < route.length; i++) {
            if (i > 0) {
                time += dm.getDistance(route[i - 1], route[i]);
            }
            City c = cities[route[i]];
            if (time < c.open) {
                buf[count++] = i;
                time = c.open;
            } else if (time > c.close) {
                return new int[0];
            }
        }
        int[] result = new int[count];
        System.arraycopy(buf, 0, result, 0, count);
        return result;
    }

    private boolean[] inTourMask(int[] route) {
        boolean[] inTour = new boolean[totalCities];
        for (int c : route) {
            inTour[c] = true;
        }
        return inTour;
    }

    private int[] sampleOutOfTour(boolean[] inTour, int[] route, ThreadLocalRandom rand, int limit) {
        int[] buf = new int[limit * route.length + limit];
        int count = 0;
        boolean[] seen = new boolean[totalCities];

        int tourSamples = Math.min(route.length, 8 + limit / 5);
        for (int s = 0; s < tourSamples; s++) {
            int anchor = route[rand.nextInt(route.length)];
            int k = dm.getK();
            int neighborLimit = Math.min(k, limit);
            for (int t = 0; t < neighborLimit; t++) {
                int c = dm.getNearestCity(anchor, t);
                if (!inTour[c] && !seen[c]) {
                    seen[c] = true;
                    buf[count++] = c;
                }
            }
        }

        for (int fill = 0; fill < limit && count < buf.length; fill++) {
            int c = rand.nextInt(totalCities);
            if (!inTour[c] && !seen[c]) {
                seen[c] = true;
                buf[count++] = c;
            }
        }

        if (count == 0) {
            return new int[0];
        }
        if (count > limit) {
            shufflePrefix(buf, count, rand);
            count = limit;
        }
        int[] result = new int[count];
        System.arraycopy(buf, 0, result, 0, count);
        return result;
    }

    private void shufflePrefix(int[] arr, int n, ThreadLocalRandom rand) {
        for (int i = n - 1; i > 0; i--) {
            int j = rand.nextInt(i + 1);
            int tmp = arr[i];
            arr[i] = arr[j];
            arr[j] = tmp;
        }
    }

    private int[] candidateInsertPositions(int[] route, int city, boolean large) {
        int m = route.length + 1;
        if (!large || route.length <= 60) {
            int[] all = new int[m];
            for (int i = 0; i < m; i++) {
                all[i] = i;
            }
            return all;
        }

        int[] buf = new int[m];
        int count = 0;
        buf[count++] = 0;
        buf[count++] = route.length;
        for (int i = 0; i < route.length; i++) {
            if (dm.isNear(route[i], city, 25)) {
                if (count == 0 || buf[count - 1] != i) {
                    buf[count++] = i;
                }
                if (buf[count - 1] != i + 1) {
                    buf[count++] = i + 1;
                }
            }
        }
        if (count <= 2) {
            int[] all = new int[m];
            for (int i = 0; i < m; i++) {
                all[i] = i;
            }
            return all;
        }
        int[] result = new int[count];
        System.arraycopy(buf, 0, result, 0, count);
        return result;
    }

    private int[] insertAt(int[] route, int city, int pos) {
        int[] result = new int[route.length + 1];
        System.arraycopy(route, 0, result, 0, pos);
        result[pos] = city;
        System.arraycopy(route, pos, result, pos + 1, route.length - pos);
        return result;
    }

    private int[] removeIndices(int[] route, int[] sortedDescIndices) {
        int[] idx = sortedDescIndices.clone();
        Arrays.sort(idx);
        int[] temp = route.clone();
        int len = temp.length;
        for (int k = idx.length - 1; k >= 0; k--) {
            int removeAt = idx[k];
            int[] next = new int[len - 1];
            System.arraycopy(temp, 0, next, 0, removeAt);
            System.arraycopy(temp, removeAt + 1, next, removeAt, len - removeAt - 1);
            temp = next;
            len--;
        }
        return temp;
    }

    private int[] distinctIndices(int n, int count, ThreadLocalRandom rand) {
        int[] result = new int[count];
        boolean[] used = new boolean[n];
        for (int i = 0; i < count; i++) {
            int idx;
            do {
                idx = rand.nextInt(n);
            } while (used[idx]);
            used[idx] = true;
            result[i] = idx;
        }
        Arrays.sort(result);
        return result;
    }

    private int[] relocateCity(int[] order, int from, int to) {
        int n = order.length;
        int city = order[from];
        int[] without = new int[n - 1];
        int idx = 0;
        for (int i = 0; i < n; i++) {
            if (i == from) {
                continue;
            }
            without[idx++] = order[i];
        }

        int insertAfter = to;
        if (from < to) {
            insertAfter = to - 1;
        }
        insertAfter = Math.max(0, Math.min(insertAfter, without.length));

        int[] result = new int[n];
        int pos = 0;
        for (int i = 0; i < insertAfter; i++) {
            result[pos++] = without[i];
        }
        result[pos++] = city;
        for (int i = insertAfter; i < without.length; i++) {
            result[pos++] = without[i];
        }
        return result;
    }

    private static final class MoveResult {
        final Tour tour;
        final String key;

        MoveResult(Tour tour, String key) {
            this.tour = tour;
            this.key = key;
        }
    }
}
