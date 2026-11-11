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
    const val RESULT = "result/{profileId}/{stars}"
    const val ABOUT = "about"

    fun grades(profileId: Long) = "grades/$profileId"
    fun path(profileId: Long) = "path/$profileId"
    fun lesson(profileId: Long, unitId: String, number: Int) = "lesson/$profileId/$unitId/$number"
    fun result(profileId: Long, stars: Int) = "result/$profileId/$stars"
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
                onAbout = { nav.navigate(Routes.ABOUT) },
            )
        }
        composable(
            Routes.LESSON,
            listOf(profileArg, navArgument("unitId") { type = NavType.StringType }, navArgument("number") { type = NavType.IntType }),
        ) { entry ->
            val args = entry.arguments!!
            val profileId = args.getLong("profileId")
            val request = LessonRequest.UnitLesson(args.getString("unitId")!!, args.getInt("number"))
            LessonScreen(
                container = container,
                profileId = profileId,
                request = request,
                onClose = { nav.popBackStack() },
                onFinished = { stars ->
                    nav.navigate(Routes.result(profileId, stars)) { popUpTo(Routes.PATH) }
                },
            )
        }
        composable(Routes.RESULT, listOf(profileArg, navArgument("stars") { type = NavType.IntType })) { entry ->
            ResultScreen(stars = entry.arguments!!.getInt("stars")) { nav.popBackStack() }
        }
        composable(Routes.ABOUT) {
            AboutScreen { nav.popBackStack() }
        }
    }
}
