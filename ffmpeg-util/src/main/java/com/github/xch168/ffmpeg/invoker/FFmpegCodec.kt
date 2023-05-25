package com.github.xch168.ffmpeg.invoker

import kotlin.math.max
import kotlin.math.min

object FFmpegCodec {

    fun gifToImages(
        src: String,
        dsts: String,
        callback: FFmpegInvoker.Callback
    ) {
        val cmd = FFmpegCmd().apply {
            append("-i")
            append(src)
            append(dsts)
        }.build()
        FFmpegInvoker.exec(cmd, callback)
    }

    /**
     * 图片转GIF，支持的图片格式：png，jpg
     * @param srcList List<String>
     * @param dst String
     * @param scale Float
     * @param fps Int
     * @param loop Int
     * @param callback Callback
     */
    fun imagesToGif(
        srcList: List<String>,
        dst: String,
        scale: Float = 0.5F,
        fps: Int = 2,
        loop: Int = 0,
        callback: FFmpegInvoker.Callback
    ) {
        val cmd = FFmpegCmd().apply {
            for (src in srcList) {
                append("-i")
                append(src)
            }
            append("-vf")
            append("scale=iw*${scale}:ih*${scale}:flags=lanczos,split[s0][s1];[s0]palettegen[p];[s1][p]paletteuse")
            append("-loop")
            append(loop.toString())
            append(dst)
        }.build()
        FFmpegInvoker.exec(cmd, callback)
    }

    /**
     * 视频转GIF
     * @param src String              视频文件路径
     * @param dst String              输出GIF文件路径
     * @param startTime Long          视频起始时间
     * @param endTime Long            视频结束时间
     * @param scale Float             分辨率缩放 0.0F ~ 1.0F
     * @param fps Int                 帧率
     * @param loop Int                0无限循环，-1不循环，1播放两次，2播放三次，10播放十次...
     */
    fun videoToGif(
        src: String,
        dst: String,
        startTime: Long = 0L,
        endTime: Long = 3000L,
        scale: Float = 1F,
        fps: Int = 10,
        loop: Int = 0,
        cropWidth: Int,
        cropHeight: Int,
        cropPositionX: Int,
        cropPositionY: Int,
        callback: FFmpegInvoker.Callback
    ) {
        val cmd = FFmpegCmd().apply {
            append("-i")
            append(src)
            append("-vf")
            append("fps=$fps,crop=${cropWidth}:${cropHeight}:${cropPositionX + 1}:${cropPositionY - 10},scale=iw*${scale}:ih*${scale}:flags=lanczos,split[s0][s1];[s0]palettegen[p];[s1][p]paletteuse")
            append("-loop")
            append(loop.toString())
            append(dst)
        }.build()
        FFmpegInvoker.exec(cmd, callback)
    }

//    fun videoToGif(
//        src: String,
//        dst: String,
//        startTime: Long = 0L,
//        endTime: Long = 3000L,
//        scale: Float = 0.5F,
//        fps: Int = 10,
//        loop: Int = 10,
//        speed: Float = 1.0F,        // 倍速 一倍速，二倍速等等..
//        stickers: List<Sticker>,
//        callback: FFmpegInvoker.Callback
//    ) {
//        val cmd = FFmpegCmd().apply {
//            append("-ss")
//            append("${startTime}ms")
//            append("-t")
//            append("${endTime}ms")
//            append("-i")
//            append(src)
//            val imageStickers = mutableListOf<ImageSticker>()
//            val textStickers = mutableListOf<TextSticker>()
//            for (sticker in stickers) {
//                if (sticker is ImageSticker) {
//                    imageStickers += sticker
//                    append("-i")
//                    append(sticker.src)
//                } else if (sticker is TextSticker) {
//                    textStickers += sticker
//                }
//            }
//            append("-filter_complex")
//            var filterComplexCommand = ""
//            /* ------------------------------------------- 图片贴纸操作开始 ------------------------------------------------*/
//            // 1、将每个贴纸进行旋转缩放，操作顺序：scale -> rotate
//            imageStickers.forEachIndexed { index, sticker ->
//                filterComplexCommand += "[${index + 1}:v]scale=${sticker.scaleWidth()}:${sticker.scaleHeight()},rotate=${sticker.rotation}:c=0x00000000[image_sticker_${index + 1}];"
//            }
//            // 2、将每个贴纸叠加到目标上
//            imageStickers.forEachIndexed { index, sticker ->
//                filterComplexCommand += if (index == 0) {
//                    "[0:v][image_sticker_${index + 1}]overlay=${sticker.scaleX()}:${sticker.scaleY()}:enable='between(n,${sticker.startIndex},${sticker.endIndex})'[out_sticker_${index + 1}];"
//                } else {
//                    "[out_sticker_${index}][image_sticker_${index + 1}]overlay=${sticker.scaleX()}:${sticker.scaleY()}:enable=between(n,${sticker.startIndex},${sticker.endIndex})[out_sticker_${index + 1}];"
//                }
//            }
//            if (imageStickers.isNotEmpty()) {
//                filterComplexCommand += "[out_sticker_${imageStickers.size}]fps=${fps},setpts=${1 / speed}*PTS,scale=iw*${scale}:ih*${scale}:flags=lanczos,split[s0][s1];[s0]palettegen[p];[s1][p]paletteuse"
//                append(filterComplexCommand)
//            }
//            /* ------------------------------------------- 图片贴纸操作结束 ------------------------------------------------*/
//            append("-loop")
//            append(loop.toString())
//            append(dst)
//        }.build()
//        FFmpegInvoker.exec(cmd, callback)
//    }

    /**
     * 宫格拼图 2x2
     *
     * 请注意，如果输入大小不同，可能会出现未使用的间隙，因为并非所有输出视频都已使用
     */
    fun grid(srcList: List<String>, dst: String, mode: Int = 0, callback: FFmpegInvoker.Callback) {
        val layout = when (mode) {
            0 -> {      // 2x2
                "xstack=inputs=4:layout=0_0|0_h0|w0_0|w0_h0[v]"
            }

            1 -> {      // 3x3
                "xstack=inputs=9:layout=0_0|0_h0|0_h0+h1|w0_0|w0_h0|w0_h0+h1|w0+w3_0|w0+w3_h0|w0+w3_h0+h1[v]"
            }

            2 -> {       // 4x4
                "xstack=inputs=16:layout=0_0|0_h0|0_h0+h1|0_h0+h1+h2|w0_0|w0_h0|w0_h0+h1|w0_h0+h1+h2|w0+w4_0|w0+w4_h0|w0+w4_h0+h1|w0+w4_h0+h1+h2|w0+w4+w8_0|w0+w4+w8_h0|w0+w4+w8_h0+h1|w0+w4+w8_h0+h1+h2[v]"
            }

            else -> {
                ""
            }
        }
        val cmd = FFmpegCmd().apply {
            val size = srcList.size
            repeat(size) { index ->
                append("-i")
                append(srcList[index])
            }
            append("-filter_complex")
            append(layout)
            append("-map")
            append("[v]")
            append(dst)
        }.build()
        FFmpegInvoker.exec(cmd, callback)
    }

    /**
     * 去水印
     * @param src String        源文件路径
     * @param dst String        生成文件路径
     * @param srcWidth Int      源文件宽度
     * @param srcHeight Int     源文件搞度
     * @param left Int          去水印实际的左上角坐标
     * @param top Int
     * @param width Int         去水印实际宽高
     * @param height Int
     * @param callback Callback
     */
    fun delogo(
        src: String,
        dst: String,
        srcWidth: Int,
        srcHeight: Int,
        left: Int,
        top: Int,
        width: Int,
        height: Int,
        callback: FFmpegInvoker.Callback
    ) {
        val x = max(left, 1)
        val y = max(top, 1)
        val w = min(srcWidth - left - 2, width)
        val h = min(srcHeight - top - 2, height)
        val cmd = FFmpegCmd().apply {
            append("-i")
            append(src)
            append("-vf")
            append("delogo=x=${x}:y=${y}:w=${w}:h=${h}")
            append(dst)
        }.build()
        FFmpegInvoker.exec(cmd, callback)
    }
}