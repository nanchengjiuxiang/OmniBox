// :core:model
enum class ToolCategory(val titleRes: Int, val iconRes: Int) {
    DAILY(R.string.cat_daily, R.drawable.ic_cat_daily),
    MEDIA(R.string.cat_media, R.drawable.ic_cat_media),
    HARDWARE(R.string.cat_hardware, R.drawable.ic_cat_hardware),
    DEV(R.string.cat_dev, R.drawable.ic_cat_dev)
}

data class ToolDefinition(
    val id: String,
    val titleRes: Int,
    val descRes: Int,
    val category: ToolCategory,
    val iconRes: Int,
    val route: String,
    val requiredPermissions: List<String> = emptyList(),
    val supportsWidget: Boolean = false
)

// :core:data
interface ToolProvider {
    fun getToolDefinition(): ToolDefinition
    fun registerRoute(navGraphBuilder: NavGraphBuilder, navController: NavHostController)
}
