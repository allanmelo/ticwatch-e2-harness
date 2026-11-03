package com.allanmelo.harness

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.os.Parcel
import android.util.Log
import android.widget.RemoteViews
import androidx.wear.tiles.ActionBuilders
import androidx.wear.tiles.ColorBuilders.argb
import androidx.wear.tiles.DimensionBuilders
import androidx.wear.tiles.DimensionBuilders.dp
import androidx.wear.tiles.DimensionBuilders.sp
import androidx.wear.tiles.LayoutElementBuilders
import androidx.wear.tiles.ModifiersBuilders
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.ResourceBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import androidx.wear.tiles.TimelineBuilders
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

class HarnessTileService : TileService() {

    companion object {
        private const val TAG = "HarnessTileService"
        private const val RESOURCES_VERSION = "1"
        private const val ACTION_WEAR2_TILE_UPDATE = "com.google.android.clockwork.ACTION_TILE_UPDATE_REQUEST"
        private const val DESCRIPTOR_TILE_PROVIDER = "com.google.android.clockwork.tiles.ITileProvider"
        private const val DESCRIPTOR_TILES_HOST = "com.google.android.clockwork.tiles.ITilesHost"
    }

    private val wear2TileBinder = object : Binder() {
        override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
            return when (code) {
                1 -> { // TRANSACTION_onTileUpdateRequest
                    data.enforceInterface(DESCRIPTOR_TILE_PROVIDER)
                    val tileId = data.readInt()
                    val hostBinder = data.readStrongBinder()
                    reply?.writeNoException()
                    sendWear2TileUpdate(tileId, hostBinder)
                    true
                }
                2 -> { // TRANSACTION_onTileHidden
                    data.enforceInterface(DESCRIPTOR_TILE_PROVIDER)
                    val tileId = data.readInt()
                    val hostBinder = data.readStrongBinder()
                    reply?.writeNoException()
                    true
                }
                3 -> { // TRANSACTION_onTileDeactivated
                    data.enforceInterface(DESCRIPTOR_TILE_PROVIDER)
                    val tileId = data.readInt()
                    reply?.writeNoException()
                    true
                }
                IBinder.INTERFACE_TRANSACTION -> {
                    reply?.writeString(DESCRIPTOR_TILE_PROVIDER)
                    true
                }
                else -> super.onTransact(code, data, reply, flags)
            }
        }
    }

    override fun onBind(intent: Intent): IBinder? {
        if (intent.action == ACTION_WEAR2_TILE_UPDATE) {
            Log.d(TAG, "Bound via Wear OS 2 Tile action: $ACTION_WEAR2_TILE_UPDATE")
            return wear2TileBinder
        }
        return super.onBind(intent)
    }

    private fun sendWear2TileUpdate(tileId: Int, hostBinder: IBinder?) {
        if (hostBinder == null) return
        try {
            val rv = RemoteViews(packageName, R.layout.tile_harness)

            val clickIntent = Intent(this, MainActivity::class.java).apply {
                action = Intent.ACTION_MAIN
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("autoListen", true)
            }
            val pendingIntent = PendingIntent.getActivity(
                this,
                tileId,
                clickIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            rv.setOnClickPendingIntent(R.id.tile_root, pendingIntent)
            rv.setOnClickPendingIntent(R.id.tile_btn_command, pendingIntent)

            val p = Parcel.obtain()
            val r = Parcel.obtain()
            try {
                p.writeInterfaceToken(DESCRIPTOR_TILES_HOST)
                p.writeInt(tileId)
                p.writeInt(1) // RemoteViews present
                rv.writeToParcel(p, 0)
                // Transaction 1: setRemoteViews(int tileId, RemoteViews rv)
                hostBinder.transact(1, p, r, 0)
                r.readException()
                Log.d(TAG, "Sent Wear OS 2 tile update successfully for tileId $tileId")
            } finally {
                p.recycle()
                r.recycle()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send Wear OS 2 tile update", e)
        }
    }

    // ==========================================
    // Jetpack Wear OS 3+ Fallback Implementation
    // ==========================================
    override fun onTileRequest(requestParams: RequestBuilders.TileRequest): ListenableFuture<TileBuilders.Tile> {
        val clickAction = ActionBuilders.LaunchAction.Builder()
            .setAndroidActivity(
                ActionBuilders.AndroidActivity.Builder()
                    .setPackageName(packageName)
                    .setClassName(MainActivity::class.java.name)
                    .build()
            )
            .build()

        val clickable = ModifiersBuilders.Clickable.Builder()
            .setOnClick(clickAction)
            .setId("harness_quick_command")
            .build()

        val headerText = LayoutElementBuilders.Text.Builder()
            .setText("HARNESS // AGENT")
            .setFontStyle(
                LayoutElementBuilders.FontStyle.Builder()
                    .setSize(sp(12f))
                    .setColor(argb(0xFF00E5FF.toInt()))
                    .setWeight(LayoutElementBuilders.FONT_WEIGHT_BOLD)
                    .build()
            )
            .build()

        val agentText = LayoutElementBuilders.Text.Builder()
            .setText("HERMES-01")
            .setFontStyle(
                LayoutElementBuilders.FontStyle.Builder()
                    .setSize(sp(18f))
                    .setColor(argb(0xFFFFFFFF.toInt()))
                    .setWeight(LayoutElementBuilders.FONT_WEIGHT_BOLD)
                    .build()
            )
            .build()

        val statusText = LayoutElementBuilders.Text.Builder()
            .setText("STATUS: STANDBY")
            .setFontStyle(
                LayoutElementBuilders.FontStyle.Builder()
                    .setSize(sp(11f))
                    .setColor(argb(0xFFAAAAAA.toInt()))
                    .build()
            )
            .build()

        val buttonText = LayoutElementBuilders.Text.Builder()
            .setText("[ COMMAND ]")
            .setFontStyle(
                LayoutElementBuilders.FontStyle.Builder()
                    .setSize(sp(13f))
                    .setColor(argb(0xFF000000.toInt()))
                    .setWeight(LayoutElementBuilders.FONT_WEIGHT_BOLD)
                    .build()
            )
            .build()

        val buttonBox = LayoutElementBuilders.Box.Builder()
            .setWidth(dp(130f))
            .setHeight(dp(36f))
            .setModifiers(
                ModifiersBuilders.Modifiers.Builder()
                    .setClickable(clickable)
                    .setBackground(
                        ModifiersBuilders.Background.Builder()
                            .setColor(argb(0xFF00E5FF.toInt()))
                            .setCorner(ModifiersBuilders.Corner.Builder().setRadius(dp(18f)).build())
                            .build()
                    )
                    .build()
            )
            .addContent(buttonText)
            .build()

        val column = LayoutElementBuilders.Column.Builder()
            .setWidth(dp(180f))
            .setHeight(dp(180f))
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
            .addContent(headerText)
            .addContent(LayoutElementBuilders.Spacer.Builder().setHeight(dp(6f)).build())
            .addContent(agentText)
            .addContent(statusText)
            .addContent(LayoutElementBuilders.Spacer.Builder().setHeight(dp(12f)).build())
            .addContent(buttonBox)
            .build()

        val rootBox = LayoutElementBuilders.Box.Builder()
            .setWidth(DimensionBuilders.expand())
            .setHeight(DimensionBuilders.expand())
            .setModifiers(
                ModifiersBuilders.Modifiers.Builder()
                    .setBackground(
                        ModifiersBuilders.Background.Builder()
                            .setColor(argb(0xFF00E5FF.toInt()))
                            .build()
                    )
                    .build()
            )
            .addContent(column)
            .build()

        val layout = LayoutElementBuilders.Layout.Builder()
            .setRoot(rootBox)
            .build()

        val timelineEntry = TimelineBuilders.TimelineEntry.Builder()
            .setLayout(layout)
            .build()

        val timeline = TimelineBuilders.Timeline.Builder()
            .addTimelineEntry(timelineEntry)
            .build()

        val tile = TileBuilders.Tile.Builder()
            .setResourcesVersion(RESOURCES_VERSION)
            .setTimeline(timeline)
            .setFreshnessIntervalMillis(60_000)
            .build()

        return Futures.immediateFuture(tile)
    }

    override fun onResourcesRequest(requestParams: RequestBuilders.ResourcesRequest): ListenableFuture<ResourceBuilders.Resources> {
        val resources = ResourceBuilders.Resources.Builder()
            .setVersion(RESOURCES_VERSION)
            .build()
        return Futures.immediateFuture(resources)
    }
}
