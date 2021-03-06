package cz.applifting.humansis.misc

import cz.applifting.humansis.BuildConfig

enum class ApiEnvironments(val id: Int, val url: String) {
    FRONT(0, BuildConfig.FRONT_API_URL),
    DEMO(1, BuildConfig.DEMO_API_URL),
    STAGE(2, BuildConfig.STAGE_API_URL),
    DEV(3, BuildConfig.DEV_API_URL),
    TEST(4, BuildConfig.TEST_API_URL),
    RELEASE(5, BuildConfig.RELEASE_API_URL);
}
