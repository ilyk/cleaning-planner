package com.ilyk.cleaningplanner.data.fake

data class RoomUi(val id: String, val name: String, val qrSlug: String)

object RoomsFakeRepo {
    val rooms = listOf(
        RoomUi("r1", "Kitchen", "qr-kitchen-123"),
        RoomUi("r2", "Living Room", "qr-living-456"),
        RoomUi("r3", "Bathroom", "qr-bath-789")
    )
}

