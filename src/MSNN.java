import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public class MSNN {
    private static final int STRATEGIC_STARTS = 200;
    private static final int PATIENCE = 200;

    private final City[] cities;
    private final DistanceMatrix dm;

    public MSNN(City[] cities, DistanceMatrix dm) {
        this.cities = cities;
        this.dm = dm;
    }

    public Tour multiStartNN(int lookAhead) {
        int n = cities.length;
        int cap = Math.min(STRATEGIC_STARTS, n);
        int randomStarts = (n <= 500) ? n : (n <= 5000) ? 1000 : 2000;

        Tour best = null;
        ThreadLocalRandom rand = ThreadLocalRandom.current();
        Set<Integer> usedStarts = new HashSet<>();
        int[] noImprovementCount = {0};

        // Time-window strategic starts
        for (int start : selectStrategicStartsByWindow(cap)) {
            if (!usedStarts.add(start)) {
                continue;
            }
            best = tryStart(start, lookAhead, best, noImprovementCount);
            if (noImprovementCount[0] >= PATIENCE) {
                return best;
            }
        }

        // Dense-region strategic starts
        if (n > cap) {
            for (int start : selectStrategicStartsByLocation(cap)) {
                if (!usedStarts.add(start)) {
                    continue;
                }
                best = tryStart(start, lookAhead, best, noImprovementCount);
                if (noImprovementCount[0] >= PATIENCE) {
                    return best;
                }
            }
        }

        // Random restart starts
        for (int iter = 0; iter < randomStarts; iter++) {
            int start = (n <= 500) ? iter : rand.nextInt(n);
            if (!usedStarts.add(start)) {
                continue;
            }
            best = tryStart(start, lookAhead, best, noImprovementCount);
            if (noImprovementCount[0] >= PATIENCE) {
                return best;
            }
        }
        return best;
    }

    private Tour tryStart(int start, int lookAhead, Tour best, int[] noImprovementCount) {
        Tour t = buildTour(start, lookAhead);
        if (best == null || Tour.isBetter(t, best)) {
            noImprovementCount[0] = 0;
            return t;
        }
        noImprovementCount[0]++;
        return best;
    }

    public Tour multiStartNNBoth() {
        return optimizeTourStart(multiStartNN(2));
    }

    // Rotate valid tour starts
    private Tour optimizeTourStart(Tour tour) {
        int m = tour.visitedCount;
        if (m <= 1) {
            return tour;
        }

        Tour best = tour;
        for (int i = 1; i < m; i++) {
            int[] rotated = new int[m];
            for (int j = 0; j < m; j++) {
                rotated[j] = tour.cities[(i + j) % m];
            }
            if (!Tour.isValid(rotated, cities, dm)) {
                continue;
            }
            Tour candidate = new Tour(rotated, cities, dm);
            if (Tour.isBetter(candidate, best)) {
                best = candidate;
            }
        }
        return best;
    }

    private int[] selectStrategicStartsByWindow(int count) {
        int n = cities.length;
        if (n <= count) {
            int[] all = new int[n];
            for (int i = 0; i < n; i++) {
                all[i] = i;
            }
            return all;
        }

        int[] ww = new int[n];
        for (int i = 0; i < n; i++) {
            ww[i] = cities[i].close - cities[i].open;
        }

        Integer[] order = new Integer[n];
        for (int i = 0; i < n; i++) {
            order[i] = i;
        }
        Arrays.sort(order, (a, b) -> Integer.compare(ww[a], ww[b]));

        int candidates = Math.min(n, Math.max(count * 2, n / 2));
        boolean[] grouped = new boolean[n];
        List<Integer> seeds = new ArrayList<>();

        for (int idx = 0; idx < candidates && seeds.size() < count; idx++) {
            int c = order[idx];
            if (grouped[c]) {
                continue;
            }
            seeds.add(c);
            grouped[c] = true;
            // Per-seed local density radius
            double localThresh = computeLocalThreshold(c);
            for (int r = 0; r < dm.getK(); r++) {
                int nb = dm.getNearestCity(c, r);
                if (grouped[nb]) {
                    continue;
                }
                if (dm.getDistance(c, nb) > localThresh) {
                    break;
                }
                if (ww[nb] <= ww[c] * 3) {
                    grouped[nb] = true;
                }
            }
        }

        int[] res = new int[seeds.size()];
        for (int i = 0; i < seeds.size(); i++) {
            res[i] = seeds.get(i);
        }
        return res;
    }

    private int[] selectStrategicStartsByLocation(int count) {
        int n = cities.length;

        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE;
        int minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE;
        for (City c : cities) {
            minX = Math.min(minX, c.x);
            maxX = Math.max(maxX, c.x);
            minY = Math.min(minY, c.y);
            maxY = Math.max(maxY, c.y);
        }

        int gs = (int) Math.ceil(Math.sqrt(count * 1.5));
        int[][] dens = new int[gs][gs];
        @SuppressWarnings("unchecked")
        List<Integer>[][] cells = new ArrayList[gs][gs];
        for (int i = 0; i < gs; i++) {
            for (int j = 0; j < gs; j++) {
                cells[i][j] = new ArrayList<>();
            }
        }

        double cw = (maxX - minX + 1.0) / gs;
        double ch = (maxY - minY + 1.0) / gs;
        for (int i = 0; i < n; i++) {
            int gx = (cw == 0) ? 0 : Math.min((int) ((cities[i].x - minX) / cw), gs - 1);
            int gy = (ch == 0) ? 0 : Math.min((int) ((cities[i].y - minY) / ch), gs - 1);
            dens[gx][gy]++;
            cells[gx][gy].add(i);
        }

        List<CellInfo> list = new ArrayList<>();
        for (int i = 0; i < gs; i++) {
            for (int j = 0; j < gs; j++) {
                if (dens[i][j] > 0) {
                    list.add(new CellInfo(i, j, dens[i][j], cells[i][j]));
                }
            }
        }
        list.sort((a, b) -> Integer.compare(b.count, a.count));

        int lim = Math.min(count, list.size());
        int[] res = new int[lim];
        for (int i = 0; i < lim; i++) {
            CellInfo ci = list.get(i);
            double cx = minX + (ci.x + 0.5) * cw;
            double cy = minY + (ci.y + 0.5) * ch;
            int best = ci.cities.get(0);
            double bd = Double.MAX_VALUE;
            for (int cidx : ci.cities) {
                double d = (cities[cidx].x - cx) * (cities[cidx].x - cx)
                        + (cities[cidx].y - cy) * (cities[cidx].y - cy);
                if (d < bd) {
                    bd = d;
                    best = cidx;
                }
            }
            res[i] = best;
        }
        return res;
    }

    private double computeLocalThreshold(int seedCity) {
        int sample = Math.min(10, dm.getK());
        if (sample == 0) {
            return 1.0;
        }
        double total = 0;
        for (int r = 0; r < sample; r++) {
            total += dm.getDistance(seedCity, dm.getNearestCity(seedCity, r));
        }
        double avg = total / sample;
        double minDist = dm.getDistance(seedCity, dm.getNearestCity(seedCity, 0));
        return Math.max(avg * 1.5, minDist * 2.0);
    }

    private static class CellInfo {
        final int x, y, count;
        final List<Integer> cities;

        CellInfo(int x, int y, int count, List<Integer> cities) {
            this.x = x;
            this.y = y;
            this.count = count;
            this.cities = cities;
        }
    }

    private Tour buildTour(int start, int lookAhead) {
        int n = cities.length;
        boolean[] visited = new boolean[n];
        int[] order = new int[n];
        int visitedCount = 0;
        int currentTime = 0;
        int currentCity = start;

        visited[start] = true;
        order[visitedCount++] = start;
        if (currentTime < cities[start].open) {
            currentTime = cities[start].open;
        }
        if (currentTime > cities[start].close) {
            return new Tour(new int[0], cities, dm);
        }

        while (true) {
            int nextCity = selectNext(currentCity, currentTime, visited, lookAhead);
            if (nextCity == -1) {
                break;
            }
            visited[nextCity] = true;
            order[visitedCount++] = nextCity;
            currentTime += dm.getDistance(currentCity, nextCity);
            if (currentTime < cities[nextCity].open) {
                currentTime = cities[nextCity].open;
            }
            currentCity = nextCity;
        }

        int[] result = new int[visitedCount];
        System.arraycopy(order, 0, result, 0, visitedCount);
        return new Tour(result, cities, dm);
    }

    private int selectNext(int currentCity, int currentTime, boolean[] visited, int lookAhead) {
        int nextCity = -1;
        int bestCost = Integer.MAX_VALUE;
        for (int i : collectCandidates(currentCity, visited)) {
            int cost = evaluateCandidate(currentCity, currentTime, i, visited, lookAhead);
            if (cost < bestCost) {
                bestCost = cost;
                nextCity = i;
            }
        }
        return nextCity;
    }

    private int[] collectCandidates(int currentCity, boolean[] visited) {
        int n = cities.length;
        int k = dm.getK();
        int count = 0;
        int[] buf = new int[k];
        for (int r = 0; r < k; r++) {
            int i = dm.getNearestCity(currentCity, r);
            if (!visited[i]) {
                buf[count++] = i;
            }
        }
        if (count == 0 && n <= 500) {
            int[] f = new int[n];
            int c = 0;
            for (int i = 0; i < n; i++) {
                if (!visited[i]) {
                    f[c++] = i;
                }
            }
            int[] r = new int[c];
            System.arraycopy(f, 0, r, 0, c);
            return r;
        }
        int[] res = new int[count];
        System.arraycopy(buf, 0, res, 0, count);
        return res;
    }

    private int evaluateCandidate(int currentCity, int currentTime, int i, boolean[] visited, int lookAhead) {
        int travel = dm.getDistance(currentCity, i);
        int arrival = currentTime + travel;
        if (arrival > cities[i].close) {
            return Integer.MAX_VALUE;
        }
        if (arrival < cities[i].open) {
            arrival = cities[i].open;
        }
        if (lookAhead == 1) {
            return travel;
        }
        // Two-step lookahead cost
        int bestNextLeg = Integer.MAX_VALUE;
        boolean[] v2 = visited.clone();
        v2[i] = true;
        for (int j : collectCandidates(i, v2)) {
            if (j == i) {
                continue;
            }
            int travel2 = dm.getDistance(i, j);
            int arrival2 = arrival + travel2;
            if (arrival2 > cities[j].close) {
                continue;
            }
            if (arrival2 < cities[j].open) {
                arrival2 = cities[j].open;
            }
            if (arrival2 > cities[j].close) {
                continue;
            }
            if (travel2 < bestNextLeg) {
                bestNextLeg = travel2;
            }
        }
        return travel + (bestNextLeg == Integer.MAX_VALUE ? 0 : bestNextLeg);
    }
}
