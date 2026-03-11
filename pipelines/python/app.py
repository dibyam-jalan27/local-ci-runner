import json
import math


def fibonacci(n):
    a, b = 0, 1
    for _ in range(n):
        a, b = b, a + b
    return a


def stats(data):
    mean = sum(data) / len(data)
    variance = sum((x - mean) ** 2 for x in data) / len(data)
    return {"mean": mean, "std_dev": round(math.sqrt(variance), 4)}


if __name__ == "__main__":
    fibs = [fibonacci(i) for i in range(15)]
    print(f"Fibonacci(15): {fibs}")

    result = stats([10, 20, 30, 40, 50])
    print(f"Stats: {json.dumps(result)}")

    assert result["mean"] == 30.0, "Mean check failed"
    assert fibonacci(10) == 55, "Fibonacci check failed"

    print("All checks passed!")
