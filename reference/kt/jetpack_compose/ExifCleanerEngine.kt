package com.omnibox.feature.exif

import androidx.exifinterface.media.ExifInterface
import java.io.File

class ExifCleanerEngine {
    fun stripSensitiveData(file: File, stripGps: Boolean, stripSerial: Boolean, stripAll: Boolean) {
        val exif = ExifInterface(file.absolutePath)
        if (stripAll) {
            // 完全重置核心元数据
            exif.setLatLong(0.0, 0.0)
            exif.setAttribute(ExifInterface.TAG_DATETIME, null)
            exif.setAttribute(ExifInterface.TAG_MODEL, null)
            exif.setAttribute(ExifInterface.TAG_MAKE, null)
        } else {
            if (stripGps) {
                exif.setLatLong(0.0, 0.0)
                exif.setAttribute(ExifInterface.TAG_GPS_LATITUDE, null)
                exif.setAttribute(ExifInterface.TAG_GPS_LONGITUDE, null)
                exif.setAttribute(ExifInterface.TAG_GPS_ALTITUDE, null)
            }
            if (stripSerial) {
                exif.setAttribute(ExifInterface.TAG_BODY_SERIAL_NUMBER, null)
                exif.setAttribute(ExifInterface.TAG_LENS_SERIAL_NUMBER, null)
            }
        }
        exif.saveAttributes()
    }
}
