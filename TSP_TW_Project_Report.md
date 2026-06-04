# CSE2246 Assignment 2: Traveling Salesman Problem with Time Windows

**Berkan Güneş-150121026 & Yakup Mert Aslan-150119765**

---

## 1. Introduction

The Traveling Salesman Problem with Time Windows (TSP-TW) is a variant of the classic TSP in which each city can only be visited within a specified time interval [open, close]. The salesman starts at time 0, travels at unit speed, and must return to the starting city. The primary objective is to maximize the number of visited cities; among equal counts, the shorter tour length is preferred. This problem is NP-hard, making exact optimization infeasible for large instances. We designed a hierarchical heuristic pipeline combining a Multi-Start Nearest Neighbor (MSNN) construction heuristic, a Tabu Search metaheuristic for improving visit count, and a 2-opt local search for tour length reduction.

---

## 2. Algorithm Design

Our solution follows a three-phase pipeline:

**Phase 1 - Multi-Start Nearest Neighbor (MSNN):** We generate multiple candidate tours using strategic and random starting cities. Strategic starts are selected based on narrow time windows and dense geographic regions. A 2-step lookahead cost function guides city selection, considering not only the immediate travel distance but also the best next leg from each candidate. Early stopping (patience = 200) prevents excessive computation when no improvement is observed.

**Phase 2 - Tabu Search:** Starting from the best MSNN tour, we apply a tabu metaheuristic with four operators: ADD (insert out-of-tour cities), REMOVE-ADD-CHAIN (remove multiple cities then greedily re-insert), WAITING_REDUCE (relocate cities that cause early waiting), and RELOCATE (random city repositioning). Operator selection is biased toward ADD and REMOVE-ADD-CHAIN for large instances. Tabu tenure and iteration count are scaled by instance size.

**Phase 3 - 2-opt Local Search:** A candidate-restricted 2-opt sweep removes path crossings and shortens total tour length without changing the visited city set. Only k-nearest-neighbor edges are considered to keep complexity manageable.

---

## 3. Time-Window Constraint Satisfaction

Time windows are enforced during tour simulation. When computing tour statistics, we track elapsed time including travel and waiting. If arrival time t < open, we wait until t = open; if t > close, the tour is rejected as invalid. Every candidate move in Tabu Search and 2-opt is validated through full simulation before acceptance. This ensures all reported tours strictly satisfy time-window constraints. The verifier confirms this by independently recomputing distances and checking feasibility.

---

## 4. Experimental Results

We tested our solver on four benchmark instances provided by the course. All outputs passed the verifier check (VALID). Table summarizes the results.

| Instance | Cities | Visited | Length | Completion |
|----------|--------|---------|--------|------------|
| Test 1   | 318    | 88      | 13,150 | 13,573     |
| Test 2   | 984    | 212     | 681    | 682        |
| Test 3   | 7,397  | 582     | 1,671,558 | 1,797,915 |
| Test 4   | 50,000 | 658     | 52,203 | 52,203     |

![](data:image/png;base64...)

![](data:image/png;base64...)

![](data:image/png;base64...)

![](data:image/png;base64...)

---

## 5. Limitations and Future Work

Our solver is heuristic and offers no optimality guarantee. This project posed significant implementation challenges: external libraries were prohibited, so we built all data structures from scratch in pure Java, including a custom k-nearest-neighbor DistanceMatrix and manual time-window simulation in the Tour class. We experimented with a bidirectional MSNN approach (simultaneous forward and backward construction from a mid-interval starting point), but abandoned it because the optimal split point was difficult to determine for arbitrary time windows. Better solutions may be achievable with more sophisticated methods such as Adaptive Large Neighborhood Search (ALNS) or genetic algorithms, but these were impractical within the project timeframe. Additionally, Tabu Search parameters are conservative due to runtime constraints; higher iteration counts could improve results at the cost of longer execution.

---

## References

[1] S. Luke, *Essentials of Metaheuristics*, 2nd ed. Lulu, 2013, pp. 24-25.

[2] F. W. Glover and M. Laguna, *Tabu Search*. Boston, MA: Kluwer Academic Publishers, 1997.

[3] J. B. F. Jr, "OPTTSP - Orienteering Problem with Time Windows," GitHub repository. Available: https://github.com/alexfrt/tabu

---

## Division of Labor

Algorithm design was primarily led by Berkan, while implementation was primarily led by Mert. Both team members collaborated closely throughout the project on design decisions, debugging, and testing.
