public class Tour {
    public int[] cities;
    public int visitedCount;
    public int totalLength;
    public int completionTime;

    public Tour(int[] cityOrder, City[] allCities, DistanceMatrix dm) {
        this.cities = cityOrder.clone();
        this.visitedCount = cityOrder.length;
        if (allCities != null && dm != null) {
            computeStats(allCities, dm);
        }
    }

    // Simulate tour with time windows
    private void computeStats(City[] allCities, DistanceMatrix dm) {
        totalLength = 0;
        completionTime = 0;
        int n = visitedCount;
        if (n == 0) {
            return;
        }

        int time = 0;
        for (int i = 0; i < n; i++) {
            City c = allCities[cities[i]];
            int travel = (i == 0) ? 0 : dm.getDistance(cities[i - 1], cities[i]);
            totalLength += travel;
            time += travel;
            if (time > c.close) {
                throw new IllegalArgumentException("Invalid tour at city " + c.id);
            }
            if (time < c.open) {
                time = c.open;
            }
        }

        int returnDist = dm.getDistance(cities[n - 1], cities[0]);
        totalLength += returnDist;
        time += returnDist;
        completionTime = time;
    }

    // Lexicographic tour comparison
    public static boolean isBetter(Tour a, Tour b) {
        if (a.visitedCount != b.visitedCount) {
            return a.visitedCount > b.visitedCount;
        }
        if (a.totalLength != b.totalLength) {
            return a.totalLength < b.totalLength;
        }
        return a.completionTime < b.completionTime;
    }

    public static boolean isValid(int[] order, City[] allCities, DistanceMatrix dm) {
        try {
            new Tour(order, allCities, dm);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    public Tour copy(City[] allCities, DistanceMatrix dm) {
        return new Tour(cities, allCities, dm);
    }
}
