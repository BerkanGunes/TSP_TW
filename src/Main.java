import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

public class Main {
    public static void main(String[] args) throws IOException {
        if (args.length < 2) {
            System.err.println("Usage: java Main input.txt output.txt");
            return;
        }
        long startTime = System.nanoTime();

        // Read input cities
        List<City> cityList = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new FileReader(args[0]))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) {
                    continue;
                }
                String[] parts = line.split("\\s+");
                if (parts.length < 5) {
                    continue;
                }
                int id = Integer.parseInt(parts[0]);
                int x = Integer.parseInt(parts[1]);
                int y = Integer.parseInt(parts[2]);
                int open = Integer.parseInt(parts[3]);
                int close = Integer.parseInt(parts[4]);
                cityList.add(new City(id, x, y, open, close));
            }
        }
        City[] cities = cityList.toArray(new City[0]);
        int n = cities.length;
        System.out.println("Read " + n + " cities");

        // Precompute k-nearest neighbors
        int k = Math.min(100, Math.max(0, n - 1));
        DistanceMatrix dm = new DistanceMatrix(cities, k);
        System.out.println("Distance matrix ready (k=" + k + ")");

        // Multi-start nearest neighbor
        MSNN msnn = new MSNN(cities, dm);
        Tour best = msnn.multiStartNNBoth();
        double msnnSeconds = (System.nanoTime() - startTime) / 1e9;
        System.out.println("Initial MSNN: " + best.visitedCount + " cities, length " + best.totalLength);
        System.out.println("MSNN time: " + msnnSeconds + " sec");

        // Tabu search improvement
        if (n <= 50000 && best.visitedCount >= 3) {
            int tabuIter = n <= 500 ? 5000 : (n <= 5000 ? 2000 : 7500);
            int tabuTenure = n < 400 ? 10 : (n < 5000 ? 20 : 35);
            long tabuStart = System.nanoTime();
            TabuSearch ts = new TabuSearch(dm, cities);
            best = ts.improve(best, tabuIter, tabuTenure);
            double tabuSeconds = (System.nanoTime() - tabuStart) / 1e9;
            System.out.println("After Tabu: " + best.visitedCount + " cities, length " + best.totalLength);
            System.out.println("Tabu time: " + tabuSeconds + " sec");
        }

        // Shorten tour with 2-opt
        LocalSearch ls = new LocalSearch(dm, cities, 50);
        best = ls.twoOpt(best);
        System.out.println("After 2-opt: " + best.visitedCount + " cities, length " + best.totalLength);

        // Write solution file
        try (PrintWriter pw = new PrintWriter(new FileWriter(args[1]))) {
            pw.println(best.visitedCount + " " + best.totalLength + " " + best.completionTime);
            for (int i = 0; i < best.visitedCount; i++) {
                pw.println(best.cities[i]);
            }
            pw.println();
        }

        double seconds = (System.nanoTime() - startTime) / 1e9;
        System.out.println("Total time: " + seconds + " sec");
    }
}
