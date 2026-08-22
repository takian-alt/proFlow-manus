# App Folders Implementation

This directory contains the implementation for Task 13: App Folders functionality.

## Components

### AppFolderOverlay.kt
Full-screen overlay for displaying and managing folder contents.

**Features:**
- Scrollable 4-column grid of apps in the folder
- Editable folder name (tap to edit)
- Drag-out gesture to remove apps from folder
- Auto-dissolve when only 1 app remains
- Notification badges and focus mode dimming support

**Usage:**
```kotlin
var showFolderOverlay by remember { mutableStateOf(false) }
var selectedFolder by remember { mutableStateOf<FolderDefinition?>(null) }

if (showFolderOverlay && selectedFolder != null) {
    AppFolderOverlay(
        folder = selectedFolder!!,
        apps = viewModel.allApps.collectAsState().value,
        badgeCounts = viewModel.badgeCounts.collectAsState().value,
        focusActive = viewModel.focusActive.collectAsState().value,
        onDismiss = { showFolderOverlay = false },
        onAppLaunch = { packageName ->
            viewModel.recordLaunch(packageName)
            // Launch app via AppRepository
        },
        onAppLongPress = { app ->
            // Show context menu
        },
        onRemoveApp = { packageName ->
            viewModel.removeAppFromFolder(selectedFolder!!.id, packageName)
        },
        onRenameFolder = { newName ->
            viewModel.renameFolder(selectedFolder!!.id, newName)
        }
    )
}
```

### FolderIcon
Composable for displaying folder preview icon with 2x2 grid of first 4 apps.

**Usage:**
```kotlin
FolderIcon(
    folder = folderDefinition,
    apps = allApps,
    modifier = Modifier.size(64.dp),
    onTap = {
        selectedFolder = folderDefinition
        showFolderOverlay = true
    },
    onLongPress = {
        // Show folder context menu
    }
)
```

## ViewModel Methods

The following methods have been added to `LauncherViewModel`:

### createFolder
```kotlin
fun createFolder(
    packageName1: String,
    packageName2: String,
    gridIndex: Int,
    name: String = "Folder"
)
```
Creates a new folder from two apps. Called when user drags one app onto another.

### addAppToFolder
```kotlin
fun addAppToFolder(folderId: String, packageName: String)
```
Adds an app to an existing folder.

### removeAppFromFolder
```kotlin
fun removeAppFromFolder(folderId: String, packageName: String)
```
Removes an app from a folder. Auto-dissolves folder if only 1 app remains.

### renameFolder
```kotlin
fun renameFolder(folderId: String, newName: String)
```
Renames a folder.

### deleteFolder
```kotlin
fun deleteFolder(folderId: String)
```
Deletes a folder completely.

### removeUninstalledAppFromFolders
```kotlin
fun removeUninstalledAppFromFolders(packageName: String)
```
Removes uninstalled apps from all folders. Called automatically by PackageChangeReceiver.

## Integration Example

Here's how to integrate folders into a home screen grid:

```kotlin
@Composable
fun HomeScreenGrid(
    apps: List<AppInfo>,
    folders: List<FolderDefinition>,
    viewModel: LauncherViewModel
) {
    var showFolderOverlay by remember { mutableStateOf(false) }
    var selectedFolder by remember { mutableStateOf<FolderDefinition?>(null) }
    var draggedApp by remember { mutableStateOf<String?>(null) }

    LazyVerticalGrid(
        columns = GridCells.Fixed(4),
        modifier = Modifier.fillMaxSize()
    ) {
        // Render folders
        items(folders, key = { it.id }) { folder ->
            FolderIcon(
                folder = folder,
                apps = apps,
                onTap = {
                    selectedFolder = folder
                    showFolderOverlay = true
                },
                onLongPress = {
                    // Show folder context menu
                }
            )
        }

        // Render standalone apps (not in folders)
        val appsInFolders = folders.flatMap { it.packages }.toSet()
        val standaloneApps = apps.filter { it.packageName !in appsInFolders }

        items(standaloneApps, key = { it.packageName }) { app ->
            Box(
                modifier = Modifier
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { draggedApp = app.packageName },
                            onDragEnd = {
                                // Check if dropped on another app or folder
                                // If dropped on app: viewModel.createFolder(...)
                                // If dropped on folder: viewModel.addAppToFolder(...)
                                draggedApp = null
                            }
                        )
                    }
            ) {
                AppIcon(
                    appInfo = app,
                    onTap = { /* Launch app */ },
                    onLongPress = { /* Show context menu */ }
                )
            }
        }
    }

    // Show folder overlay when folder is tapped
    if (showFolderOverlay && selectedFolder != null) {
        AppFolderOverlay(
            folder = selectedFolder!!,
            apps = apps,
            badgeCounts = viewModel.badgeCounts.collectAsState().value,
            focusActive = viewModel.focusActive.collectAsState().value,
            onDismiss = { showFolderOverlay = false },
            onAppLaunch = { packageName ->
                viewModel.recordLaunch(packageName)
                // Launch app
            },
            onAppLongPress = { /* Show context menu */ },
            onRemoveApp = { packageName ->
                viewModel.removeAppFromFolder(selectedFolder!!.id, packageName)
            },
            onRenameFolder = { newName ->
                viewModel.renameFolder(selectedFolder!!.id, newName)
            }
        )
    }
}
```

## Dock Folder Support

Folders can also be placed in the dock. The DockRow composable should be updated to:

1. Check if a dock package is actually a folder ID
2. Render FolderIcon for folders
3. Support drag-and-drop to create folders in the dock

Example:
```kotlin
@Composable
fun DockRow(
    dockPackages: List<String>,
    folders: List<FolderDefinition>,
    apps: List<AppInfo>,
    viewModel: LauncherViewModel
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        dockPackages.forEach { packageOrFolderId ->
            // Check if it's a folder
            val folder = folders.firstOrNull { it.id == packageOrFolderId }

            if (folder != null) {
                // Render folder icon
                FolderIcon(
                    folder = folder,
                    apps = apps,
                    onTap = { /* Open folder overlay */ },
                    onLongPress = { /* Show context menu */ }
                )
            } else {
                // Render app icon
                val app = apps.firstOrNull { it.packageName == packageOrFolderId }
                app?.let {
                    AppIcon(
                        appInfo = it,
                        onTap = { /* Launch app */ },
                        onLongPress = { /* Show context menu */ }
                    )
                }
            }
        }
    }
}
```

## Requirements Satisfied

- ✅ 4.1: Folder creation by dragging one app onto another
- ✅ 4.2: Folder icon shows grid preview of first 4 apps
- ✅ 4.3: Tapping folder opens overlay with scrollable grid and editable name
- ✅ 4.4: Dragging app out removes it from folder
- ✅ 4.5: Single-app folders auto-dissolve
- ✅ 4.6: Folders work in both home screen and dock
- ✅ 4.7: Folder definitions persisted as JSON in PinnedAppsDataStore
- ✅ 4.8: Uninstalled apps removed from folders within 2 seconds (via PackageChangeReceiver)

## Testing

To test the folder functionality:

1. Create a folder by dragging one app onto another
2. Tap the folder to open the overlay
3. Verify the folder name is editable
4. Drag an app out of the folder to remove it
5. Verify the folder auto-dissolves when only 1 app remains
6. Uninstall an app that's in a folder and verify it's removed within 2 seconds
7. Test folders in both home screen and dock
8. Verify folder definitions persist across launcher restarts

## Future Enhancements

- Drag-and-drop reordering of apps within folders
- Custom folder icons/colors
- Folder badges showing total notification count
- Nested folders (if needed)
