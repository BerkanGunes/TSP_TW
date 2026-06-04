import java.util.Arrays;

public class DistanceMatrix {
    private final City[] cities;
    private final int[][] nearest;
    private final int[][] dist;
    private final int k;

    public DistanceMatrix(City[] cities, int k) {
        this.cities = cities;
        this.k = k;
        int n = cities.length;
        nearest = new int[n][k];
        dist = new int[n][k];
        Integer[] indices = new Integer[n];
        for (int i = 0; i < n; i++) {
            indices[i] = i;
        }
        // Parallel k-NN for large n
        if (n >= 2000) {
            java.util.stream.IntStream.range(0, n).parallel().forEach(i -> findNearest(i, indices.clone()));
        } else {
            for (int i = 0; i < n; i++) {
                findNearest(i, indices);
            }
        }
    }

    private void findNearest(int idx, Integer[] indices) {
        int n = cities.length;
        long[] sqDist = new long[n];
        for (int j = 0; j < n; j++) {
            long dx = cities[idx].x - cities[j].x;
            long dy = cities[idx].y - cities[j].y;
            sqDist[j] = dx * dx + dy * dy;
        }
        Arrays.sort(indices, (a, b) -> Long.compare(sqDist[a], sqDist[b]));
        int count = 0;
        for (int j = 0; j < n && count < k; j++) {
            int cand = indices[j];
            if (cand == idx) {
                continue;
            }
            nearest[idx][count] = cand;
            dist[idx][count] = (int) Math.round(Math.sqrt(sqDist[cand]));
            count++;
        }
    }

    public int getDistance(int i, int j) {
        if (i == j) {
            return 0;
        }
        for (int t = 0; t < k; t++) {
            if (nearest[i][t] == j) {
                return dist[i][t];
            }
        }
        for (int t = 0; t < k; t++) {
            if (nearest[j][t] == i) {
                return dist[j][t];
            }
        }
        // On-demand distance fallback
        long dx = cities[i].x - cities[j].x;
        long dy = cities[i].y - cities[j].y;
        return (int) Math.round(Math.sqrt(dx * dx + dy * dy));
    }

    public int getK() {
        return k;
    }

    public int getNearestCity(int i, int rank) {
        return nearest[i][rank];
    }

    public boolean isNear(int a, int b, int limit) {
        int lim = Math.min(limit, k);
        for (int t = 0; t < lim; t++) {
            if (nearest[a][t] == b) {
                return true;
            }
        }
        return false;
    }

    public int[] getCandidateCities(int city, int limit) {
        int lim = Math.min(limit, k);
        int[] result = new int[lim];
        System.arraycopy(nearest[city], 0, result, 0, lim);
        return result;
    }
}
