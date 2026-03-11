#include <iostream>
#include <vector>
#include <algorithm>
#include <numeric>
#include <cassert>

template<typename T>
T vec_sum(const std::vector<T>& v) {
    return std::accumulate(v.begin(), v.end(), T{0});
}

template<typename T>
double vec_mean(const std::vector<T>& v) {
    return static_cast<double>(vec_sum(v)) / v.size();
}

int main() {
    std::vector<int> data = {10, 20, 30, 40, 50};

    assert(vec_sum(data) == 150);
    std::cout << "[PASS] sum({10..50}) == 150" << std::endl;

    assert(vec_mean(data) == 30.0);
    std::cout << "[PASS] mean({10..50}) == 30.0" << std::endl;

    std::vector<int> unsorted = {5, 3, 1, 4, 2};
    std::sort(unsorted.begin(), unsorted.end());
    assert(unsorted == std::vector<int>({1, 2, 3, 4, 5}));
    std::cout << "[PASS] sort({5,3,1,4,2}) == {1,2,3,4,5}" << std::endl;

    std::cout << "\nAll 3 tests passed!" << std::endl;
    return 0;
}
