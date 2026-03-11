#include <iostream>
#include <vector>
#include <algorithm>
#include <numeric>
#include <cassert>

template<typename T>
T sum(const std::vector<T>& v) {
    return std::accumulate(v.begin(), v.end(), T{0});
}

template<typename T>
double mean(const std::vector<T>& v) {
    return static_cast<double>(sum(v)) / v.size();
}

int main() {
    std::vector<int> data = {10, 20, 30, 40, 50};

    // Test sum
    assert(sum(data) == 150);
    std::cout << [PASS]