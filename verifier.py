import sys
import math


def dist(a, b, cities):
    _, x1, y1, _, _ = cities[a]
    _, x2, y2, _, _ = cities[b]
    return math.floor(math.sqrt((x1 - x2) ** 2 + (y1 - y2) ** 2) + 0.5)


def read_input(input_file):
    cities = {}

    with open(input_file, "r") as f:
        for line_no, line in enumerate(f, start=1):
            line = line.strip()
            if line == "":
                continue

            parts = line.split()
            if len(parts) != 5:
                raise ValueError(f"Input error at line {line_no}: expected 5 values.")

            city_id, x, y, open_time, close_time = map(int, parts)

            if city_id in cities:
                raise ValueError(f"Input error: duplicate city ID {city_id}.")

            if open_time > close_time:
                raise ValueError(f"Input error: open_time > close_time for city {city_id}.")

            cities[city_id] = (city_id, x, y, open_time, close_time)

    return cities


def read_output(output_file):
    with open(output_file, "r") as f:
        lines = [line.strip() for line in f.readlines() if line.strip() != ""]

    if len(lines) < 1:
        raise ValueError("Output error: output file is empty.")

    first = lines[0].split()
    if len(first) != 3:
        raise ValueError("Output error: first line must contain 3 values: k total_length completion_time.")

    reported_k = int(first[0])
    reported_length = int(first[1])
    reported_completion_time = int(first[2])

    route = [int(x) for x in lines[1:]]

    if len(route) != reported_k:
        raise ValueError(
            f"Output error: reported k={reported_k}, but {len(route)} city IDs are listed."
        )

    return reported_k, reported_length, reported_completion_time, route


def verify(input_file, output_file):
    cities = read_input(input_file)
    reported_k, reported_length, reported_completion_time, route = read_output(output_file)

    if reported_k == 0:
        if reported_length != 0 or reported_completion_time != 0:
            raise ValueError("Output error: empty route must have length 0 and completion time 0.")
        print("VALID output.")
        return

    seen = set()

    for city_id in route:
        if city_id not in cities:
            raise ValueError(f"Output error: city ID {city_id} does not exist in input.")

        if city_id in seen:
            raise ValueError(f"Output error: city ID {city_id} is visited more than once.")

        seen.add(city_id)

    total_length = 0
    current_time = 0

    start_city = route[0]
    _, _, _, open_time, close_time = cities[start_city]

    if current_time < open_time:
        current_time = open_time

    if current_time > close_time:
        raise ValueError(
            f"Time-window error: start city {start_city} is visited at time "
            f"{current_time}, outside [{open_time},{close_time}]."
        )

    for i in range(1, len(route)):
        prev_city = route[i - 1]
        curr_city = route[i]

        d = dist(prev_city, curr_city, cities)
        total_length += d
        arrival_time = current_time + d

        _, _, _, open_time, close_time = cities[curr_city]

        visit_time = max(arrival_time, open_time)

        if visit_time > close_time:
            raise ValueError(
                f"Time-window error: city {curr_city} is visited at time "
                f"{visit_time}, outside [{open_time},{close_time}]. "
                f"Arrival time was {arrival_time}."
            )

        current_time = visit_time

    # Return to the starting city.
    return_distance = dist(route[-1], route[0], cities)
    total_length += return_distance
    current_time += return_distance

    completion_time = current_time

    if total_length != reported_length:
        raise ValueError(
            f"Output error: reported tour length is {reported_length}, "
            f"but computed tour length is {total_length}."
        )

    if completion_time != reported_completion_time:
        raise ValueError(
            f"Output error: reported completion time is {reported_completion_time}, "
            f"but computed completion time is {completion_time}."
        )

    print("VALID output.")
    print(f"Visited cities: {reported_k}")
    print(f"Tour length: {total_length}")
    print(f"Completion time: {completion_time}")


if __name__ == "__main__":
    if len(sys.argv) != 3:
        print("Usage:")
        print("python verifier.py input.txt output.txt")
        sys.exit(1)

    try:
        verify(sys.argv[1], sys.argv[2])
    except Exception as e:
        print("INVALID output.")
        print(e)
        sys.exit(1)