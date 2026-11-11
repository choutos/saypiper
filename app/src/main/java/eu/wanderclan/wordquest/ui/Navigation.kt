package eu.wanderclan.wordquest.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import eu.wanderclan.wordquest.AppContainer
import eu.wanderclan.wordquest.game.LessonRequest

private object Routes {
    const val PROFILES = "profiles"
    const val NEW_PROFILE = "profiles/new"
    const val GRADES = "grades/{profileId}"
    const val PATH = "path/{profileId}"
    const val LESSON = "lesson/{profileId}/{unitId}/{number}"
    const val PRACTICE = "practice/{profileId}/{wordIds}"
    const val RESULT = "result/{profileId}/{stars}/{points}/{practice}?crown={crown}"
    const val ABOUT = "about"

    fun grades(profileId: Long) = "grades/$profileId"
    fun path(profileId: Long) = "path/$profileId"
    fun lesson(profileId: Long, unitId: String, number: Int) = "lesson/$profileId/$unitId/$number"
    /** Word ids are lowercase kebab-case (content rule 1), so a comma separates them safely. */
    fun practice(profileId: Long, request: LessonRequest.Practice) = "practice/$profileId/${request.wordIds.joinToString(",")}"
    fun result(profileId: Long, finished: Finished) =
        "result/$profileId/${finished.stars}/${finished.points}/${finished.practice}" + (finished.crownedUnitId?.let { "?crown=$it" } ?: "")
}

@Composable
fun WordQuestRoot(container: AppContainer, navController: NavHostController = rememberNavController()) {
    WordQuestTheme {
        CompositionLocalProvider(LocalPictures provides container.pictures) {
            WordQuestNavHost(container, navController)
        }
    }
}

@Composable
private fun WordQuestNavHost(container: AppContainer, nav: NavHostController) {
    val profileArg = navArgument("profileId") { type = NavType.LongType }
    NavHost(nav, startDestination = Routes.PROFILES) {
        composable(Routes.PROFILES) {
            ProfilesScreen(
                container = container,
                onNewProfile = { nav.navigate(Routes.NEW_PROFILE) },
                onOpen = { profile ->
                    nav.navigate(if (profile.gradeId == null) Routes.grades(profile.id) else Routes.path(profile.id))
                },
                onAbout = { nav.navigate(Routes.ABOUT) },
            )
        }
        composable(Routes.NEW_PROFILE) {
            CreateProfileScreen(container) { id ->
                nav.navigate(Routes.grades(id)) { popUpTo(Routes.PROFILES) }
            }
        }
        composable(Routes.GRADES, listOf(profileArg)) { entry ->
            val profileId = entry.arguments!!.getLong("profileId")
            ClassPickerScreen(container, profileId) {
                nav.navigate(Routes.path(profileId)) { popUpTo(Routes.PROFILES) }
            }
        }
        composable(Routes.PATH, listOf(profileArg)) { entry ->
            val profileId = entry.arguments!!.getLong("profileId")
            PathScreen(
                container = container,
                profileId = profileId,
                onChangeClass = { nav.navigate(Routes.grades(profileId)) },
                onLesson = { unitId, number -> nav.navigate(Routes.lesson(profileId, unitId, number)) },
                onPractice = { request -> nav.navigate(Routes.practice(profileId, request)) },
                onAbout = { nav.navigate(Routes.ABOUT) },
            )
        }
        composable(
            Routes.LESSON,
            listOf(profileArg, navArgument("unitId") { type = NavType.StringType }, navArgument("number") { type = NavType.IntType }),
        ) { entry ->
            val args = entry.arguments!!
            val profileId = args.getLong("profileId")
            LessonRoute(container, nav, profileId, LessonRequest.UnitLesson(args.getString("unitId")!!, args.getInt("number")))
        }
        composable(Routes.PRACTICE, listOf(profileArg, navArgument("wordIds") { type = NavType.StringType })) { entry ->
            val args = entry.arguments!!
            val request = LessonRequest.Practice(args.getString("wordIds")!!.split(","))
            LessonRoute(container, nav, args.getLong("profileId"), request)
        }
        composable(
            Routes.RESULT,
            listOf(
                profileArg,
                navArgument("stars") { type = NavType.IntType },
                navArgument("points") { type = NavType.IntType },
                navArgument("practice") { type = NavType.BoolType },
                navArgument("crown") { type = NavType.StringType; nullable = true },
            ),
        ) { entry ->
            val args = entry.arguments!!
            val crownedUnit = args.getString("crown")?.let { container.catalog.unit(it)?.first }
            ResultScreen(
                stars = args.getInt("stars"),
                points = args.getInt("points"),
                crownedUnit = crownedUnit,
                practice = args.getBoolean("practice"),
            ) { nav.popBackStack() }
        }
        composable(Routes.ABOUT) {
            AboutScreen { nav.popBackStack() }
        }
    }
}

/** A unit lesson and a practice session are the same lesson screen; only the request differs. */
@Composable
private fun LessonRoute(container: AppContainer, nav: NavHostController, profileId: Long, request: LessonRequest) {
    LessonScreen(
        container = container,
        profileId = profileId,
        request = request,
        onClose = { nav.popBackStack() },
        onFinished = { finished -> nav.navigate(Routes.result(profileId, finished)) { popUpTo(Routes.PATH) } },
    )
}
