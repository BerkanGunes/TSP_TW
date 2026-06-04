public class LocalSearch {
    private final DistanceMatrix dm;
    private final City[] cities;
    private final int candidateLimit;

    public LocalSearch(DistanceMatrix dm, City[] cities, int candidateLimit) {
        this.dm = dm;
        this.cities = cities;
        this.candidateLimit = candidateLimit;
    }

    // Candidate-restricted 2-opt
    public Tour twoOpt(Tour tour) {
        int[] t = tour.cities.clone();
        int n = t.length;
        if (n < 3) {
            return tour.copy(cities, dm);
        }

        boolean improved = true;
        while (improved) {
            improved = false;
            outer:
            for (int i = 0; i < n - 1; i++) {
                for (int j = i + 1; j < n; j++) {
                    if (!isNeighbor(t[i], t[j])) {
                        continue;
                    }
                    int delta = delta2Opt(t, i, j);
                    if (delta >= 0) {
                        continue;
                    }
                    reverse(t, i + 1, j);
                    if (Tour.isValid(t, cities, dm)) {
                        improved = true;
                        break outer;
                    }
                    reverse(t, i + 1, j);
                }
            }
        }
        return new Tour(t, cities, dm);
    }

    private int delta2Opt(int[] t, int i, int j) {
        int a = t[i];
        int b = t[i + 1];
        int c = t[j];
        int d = t[(j + 1) % t.length];
        int oldDist = dm.getDistance(a, b) + dm.getDistance(c, d);
        int newDist = dm.getDistance(a, c) + dm.getDistance(b, d);
        return newDist - oldDist;
    }

    private void reverse(int[] arr, int start, int end) {
        while (start < end) {
            int tmp = arr[start];
            arr[start] = arr[end];
            arr[end] = tmp;
            start++;
            end--;
        }
    }

    private boolean isNeighbor(int city1, int city2) {
        return dm.isNear(city1, city2, candidateLimit)
                || dm.isNear(city2, city1, candidateLimit);
    }
}
