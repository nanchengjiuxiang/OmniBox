package com.omnibox.core.designsystem

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * 设计稿使用 Material Symbols 名称字符串；这里集中映射为 Compose 矢量图标。
 * 集中一处的好处：更换图标集 / 引入自有图标资源时只需改本文件。
 */
object OmniIcon {
    fun of(name: String): ImageVector = when (name) {
        "search" -> Icons.Filled.Search
        "mic" -> Icons.Filled.Mic
        "tune" -> Icons.Filled.Tune
        "verified_user" -> Icons.Filled.VerifiedUser
        "wb_sunny" -> Icons.Filled.WbSunny
        "photo_camera" -> Icons.Filled.PhotoCamera
        "sensors" -> Icons.Filled.Sensors
        "terminal" -> Icons.Filled.Terminal
        "magic_button", "auto_awesome" -> Icons.Filled.AutoAwesome
        "qr_code_scanner" -> Icons.Filled.QrCodeScanner
        "lock_reset", "password" -> Icons.Filled.Password
        "lock" -> Icons.Filled.Lock
        "compress" -> Icons.Filled.Compress
        "widgets" -> Icons.Filled.Widgets
        "qr_code_2", "qr_code" -> Icons.Filled.QrCode
        "swap_horiz" -> Icons.Filled.SwapHoriz
        "casino" -> Icons.Filled.Casino
        "event", "calendar_month" -> Icons.Filled.CalendarMonth
        "perm_media", "image" -> Icons.Filled.Image
        "location_off" -> Icons.Filled.LocationOff
        "location_on", "my_location" -> Icons.Filled.MyLocation
        "explore" -> Icons.Filled.Explore
        "graphic_eq", "volume_up" -> Icons.Filled.GraphicEq
        "transform" -> Icons.Filled.Transform
        "code", "data_object" -> Icons.Filled.Code
        "schedule", "access_time", "timer" -> Icons.Filled.Schedule
        "text_fields" -> Icons.Filled.TextFields
        "bolt" -> Icons.Filled.Bolt
        "grid_view" -> Icons.Filled.GridView
        "star", "favorite" -> Icons.Filled.Star
        "star_border", "favorite_border" -> Icons.Filled.StarBorder
        "settings" -> Icons.Filled.Settings
        "more_vert" -> Icons.Filled.MoreVert
        "person" -> Icons.Filled.Person
        "check", "done" -> Icons.Filled.Check
        "arrow_back" -> Icons.Filled.ArrowBack
        "arrow_forward" -> Icons.Filled.ArrowForward
        "chevron_right" -> Icons.Filled.ChevronRight
        "expand_more" -> Icons.Filled.ExpandMore
        "content_copy" -> Icons.Filled.ContentCopy
        "content_paste" -> Icons.Filled.ContentPaste
        "refresh", "sync" -> Icons.Filled.Refresh
        "add" -> Icons.Filled.Add
        "delete" -> Icons.Filled.Delete
        "history" -> Icons.Filled.History
        "close", "clear" -> Icons.Filled.Close
        "play_arrow" -> Icons.Filled.PlayArrow
        "stop" -> Icons.Filled.Stop
        "flash_on" -> Icons.Filled.FlashOn
        "zoom_in" -> Icons.Filled.ZoomIn
        "share" -> Icons.Filled.Share
        "download", "save" -> Icons.Filled.Download
        "edit" -> Icons.Filled.Edit
        "info" -> Icons.Filled.Info
        "warning" -> Icons.Filled.Warning
        "palette" -> Icons.Filled.Palette
        "dark_mode" -> Icons.Filled.DarkMode
        "security" -> Icons.Filled.Security
        "storage" -> Icons.Filled.Storage
        "notifications" -> Icons.Filled.Notifications
        "calculate", "functions" -> Icons.Filled.Calculate
        "visibility" -> Icons.Filled.Visibility
        "visibility_off" -> Icons.Filled.VisibilityOff
        "link" -> Icons.Filled.Link
        "filter_list" -> Icons.Filled.FilterList
        "rotate_right" -> Icons.Filled.RotateRight
        "memory" -> Icons.Filled.Memory
        "photo_library" -> Icons.Filled.PhotoLibrary
        "high_quality", "speed" -> Icons.Filled.Speed
        "help" -> Icons.Filled.Help
        "list" -> Icons.Filled.List
        "straighten" -> Icons.Filled.Straighten
        "thermostat" -> Icons.Filled.Thermostat
        "scale" -> Icons.Filled.Scale
        "percent" -> Icons.Filled.Percent
        "numbers" -> Icons.Filled.Numbers
        "key" -> Icons.Filled.Key
        "menu" -> Icons.Filled.Menu
        "fingerprint" -> Icons.Filled.Fingerprint
        // 设计稿中的扩展 Material Symbols 名：P1 复用语义相近的已验证图标，
        // P2 引入完整 Material Symbols 资源后替换为精确图标（见 IMPLEMENTATION_STATUS.md）。
        "cached" -> Icons.Filled.Storage
        "dataset" -> Icons.Filled.List
        "inventory_2" -> Icons.Filled.Storage
        "local_fire_department" -> Icons.Filled.Bolt
        "photo_size_select_large", "photo_size_select_small" -> Icons.Filled.Image
        "push_pin" -> Icons.Filled.Star
        "security_update_good", "verified", "shield", "shield_with_heart" -> Icons.Filled.Security
        "toggle_on" -> Icons.Filled.Settings
        "swap_vert" -> Icons.Filled.SwapHoriz
        "offline_bolt" -> Icons.Filled.Bolt
        "analytics" -> Icons.Filled.Calculate
        "wifi_password", "lock_clock" -> Icons.Filled.Lock
        "hourglass_top" -> Icons.Filled.Schedule
        "device_thermostat" -> Icons.Filled.Thermostat
        "fitness_center" -> Icons.Filled.Scale
        "crop_square" -> Icons.Filled.GridView
        "science" -> Icons.Filled.Calculate
        "hard_drive" -> Icons.Filled.Storage
        "currency_exchange" -> Icons.Filled.SwapHoriz
        "space_bar", "format_size", "spellcheck" -> Icons.Filled.TextFields
        "reorder", "code_blocks", "layers" -> Icons.Filled.List
        "auto_fix_high" -> Icons.Filled.AutoAwesome
        "sync_alt" -> Icons.Filled.Refresh
        "record_voice_over" -> Icons.Filled.Mic
        "check_circle" -> Icons.Filled.Check
        "square_foot" -> Icons.Filled.Straighten
        "arrow_drop_down" -> Icons.Filled.ExpandMore
        "clear_all" -> Icons.Filled.Close
        "restart_alt" -> Icons.Filled.Refresh
        "clean_hands" -> Icons.Filled.Delete
        "download_for_offline" -> Icons.Filled.Download
        "dashboard_customize", "space_dashboard" -> Icons.Filled.Widgets
        "open_in_new" -> Icons.Filled.Share
        "vibration", "local_police" -> Icons.Filled.Notifications
        "photo_camera_back" -> Icons.Filled.PhotoCamera
        "regular_expression", "format_indent_increase", "translate", "tag" -> Icons.Filled.Code
        "format_align_justify" -> Icons.Filled.List
        "delete_sweep", "delete_forever" -> Icons.Filled.Delete
        "content_paste_search" -> Icons.Filled.ContentPaste
        else -> Icons.Filled.GridView
    }
}
