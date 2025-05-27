package com.halufun.dynamic_island
import com.halufun.dynamic_island.OverlayService
import com.halufun.dynamic_island.RichInfo

class OverlayAPI {
    public fun initOverlay() {
        val context = RichInfo("","", "none")
        setInfo(context)
    }
    public fun setInfo(layout: RichInfo) {
        // Generate an xml of the notch shape with the text/images. Needs animation support.
    }
}
