package com.bbioon.plantdisease

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory

class PlantDiseaseApp : Application(), ImageLoaderFactory {
    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .crossfade(true)
            .build()
    }
}
