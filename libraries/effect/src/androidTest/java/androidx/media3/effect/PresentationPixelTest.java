/*
 * Copyright 2021 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package androidx.media3.effect;

import static androidx.media3.common.util.Assertions.checkNotNull;
import static androidx.media3.test.utils.BitmapPixelTestUtil.MAXIMUM_AVERAGE_PIXEL_ABSOLUTE_DIFFERENCE;
import static androidx.media3.test.utils.BitmapPixelTestUtil.createArgb8888BitmapFromCurrentGlFramebuffer;
import static androidx.media3.test.utils.BitmapPixelTestUtil.createGlTextureFromBitmap;
import static androidx.media3.test.utils.BitmapPixelTestUtil.getBitmapAveragePixelAbsoluteDifferenceArgb8888;
import static androidx.media3.test.utils.BitmapPixelTestUtil.maybeSaveTestBitmapToCacheDirectory;
import static androidx.media3.test.utils.BitmapPixelTestUtil.readBitmap;
import static androidx.test.core.app.ApplicationProvider.getApplicationContext;
import static com.google.common.truth.Truth.assertThat;

import android.content.Context;
import android.graphics.Bitmap;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLSurface;
import androidx.media3.common.C;
import androidx.media3.common.VideoFrameProcessingException;
import androidx.media3.common.util.GlUtil;
import androidx.media3.common.util.Size;
import androidx.media3.test.utils.BitmapPixelTestUtil;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.io.IOException;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Pixel test for texture processing via {@link Presentation}.
 *
 * <p>Expected images are taken from an emulator, so tests on different emulators or physical
 * devices may fail. To test on other devices, please increase the {@link
 * BitmapPixelTestUtil#MAXIMUM_AVERAGE_PIXEL_ABSOLUTE_DIFFERENCE} and/or inspect the saved output
 * bitmaps as recommended in {@link DefaultVideoFrameProcessorPixelTest}.
 */
@RunWith(AndroidJUnit4.class)
public final class PresentationPixelTest {
  public static final String ORIGINAL_PNG_ASSET_PATH =
      "media/bitmap/sample_mp4_first_frame/electrical_colors/original.png";
  public static final String ASPECT_RATIO_SCALE_TO_FIT_NARROW_PNG_ASSET_PATH =
      "media/bitmap/sample_mp4_first_frame/electrical_colors/aspect_ratio_scale_to_fit_narrow.png";
  public static final String ASPECT_RATIO_SCALE_TO_FIT_WIDE_PNG_ASSET_PATH =
      "media/bitmap/sample_mp4_first_frame/electrical_colors/aspect_ratio_scale_to_fit_wide.png";
  public static final String ASPECT_RATIO_SCALE_TO_FIT_WITH_CROP_NARROW_PNG_ASSET_PATH =
      "media/bitmap/sample_mp4_first_frame/electrical_colors/aspect_ratio_scale_to_fit_with_crop_narrow.png";
  public static final String ASPECT_RATIO_SCALE_TO_FIT_WITH_CROP_WIDE_PNG_ASSET_PATH =
      "media/bitmap/sample_mp4_first_frame/electrical_colors/aspect_ratio_scale_to_fit_with_crop_wide.png";
  public static final String ASPECT_RATIO_STRETCH_TO_FIT_NARROW_PNG_ASSET_PATH =
      "media/bitmap/sample_mp4_first_frame/electrical_colors/aspect_ratio_stretch_to_fit_narrow.png";
  public static final String ASPECT_RATIO_STRETCH_TO_FIT_WIDE_PNG_ASSET_PATH =
      "media/bitmap/sample_mp4_first_frame/electrical_colors/aspect_ratio_stretch_to_fit_wide.png";

  private final Context context = getApplicationContext();

  private @MonotonicNonNull EGLDisplay eglDisplay;
  private @MonotonicNonNull EGLContext eglContext;
  private @MonotonicNonNull SingleFrameGlShaderProgram presentationShaderProgram;
  private @MonotonicNonNull EGLSurface placeholderEglSurface;
  private int inputTexId;
  private int inputWidth;
  private int inputHeight;

  @Before
  public void createGlObjects() throws IOException, GlUtil.GlException {
    eglDisplay = GlUtil.createEglDisplay();
    eglContext = GlUtil.createEglContext(eglDisplay);
    placeholderEglSurface = GlUtil.focusPlaceholderEglSurface(eglContext, eglDisplay);

    Bitmap inputBitmap = readBitmap(ORIGINAL_PNG_ASSET_PATH);
    inputWidth = inputBitmap.getWidth();
    inputHeight = inputBitmap.getHeight();
    inputTexId = createGlTextureFromBitmap(inputBitmap);
  }

  @After
  public void release() throws GlUtil.GlException, VideoFrameProcessingException {
    if (presentationShaderProgram != null) {
      presentationShaderProgram.release();
    }
    if (eglContext != null && eglDisplay != null) {
      GlUtil.destroyEglContext(eglDisplay, eglContext);
    }
  }

  @Test
  public void drawFrame_noEdits_matchesGoldenFile() throws Exception {
    String testId = "drawFrame_noEdits";
    presentationShaderProgram =
        Presentation.createForHeight(C.LENGTH_UNSET)
            .toGlShaderProgram(context, /* useHdr= */ false);
    Size outputSize = presentationShaderProgram.configure(inputWidth, inputHeight);
    setupOutputTexture(outputSize.getWidth(), outputSize.getHeight());
    Bitmap expectedBitmap = readBitmap(ORIGINAL_PNG_ASSET_PATH);

    presentationShaderProgram.drawFrame(inputTexId, /* presentationTimeUs= */ 0);
    Bitmap actualBitmap =
        createArgb8888BitmapFromCurrentGlFramebuffer(outputSize.getWidth(), outputSize.getHeight());

    maybeSaveTestBitmapToCacheDirectory(testId, /* bitmapLabel= */ "actual", actualBitmap);
    // TODO(b/207848601): Switch to using proper tooling for testing against golden data.
    float averagePixelAbsoluteDifference =
        getBitmapAveragePixelAbsoluteDifferenceArgb8888(expectedBitmap, actualBitmap, testId);
    assertThat(averagePixelAbsoluteDifference).isAtMost(MAXIMUM_AVERAGE_PIXEL_ABSOLUTE_DIFFERENCE);
  }

  @Test
  public void drawFrame_changeAspectRatio_scaleToFit_narrow_matchesGoldenFile() throws Exception {
    String testId = "drawFrame_changeAspectRatio_scaleToFit_narrow";
    presentationShaderProgram =
        Presentation.createForAspectRatio(/* aspectRatio= */ 1f, Presentation.LAYOUT_SCALE_TO_FIT)
            .toGlShaderProgram(context, /* useHdr= */ false);
    Size outputSize = presentationShaderProgram.configure(inputWidth, inputHeight);
    setupOutputTexture(outputSize.getWidth(), outputSize.getHeight());
    Bitmap expectedBitmap = readBitmap(ASPECT_RATIO_SCALE_TO_FIT_NARROW_PNG_ASSET_PATH);

    presentationShaderProgram.drawFrame(inputTexId, /* presentationTimeUs= */ 0);
    Bitmap actualBitmap =
        createArgb8888BitmapFromCurrentGlFramebuffer(outputSize.getWidth(), outputSize.getHeight());

    maybeSaveTestBitmapToCacheDirectory(testId, /* bitmapLabel= */ "actual", actualBitmap);
    // TODO(b/207848601): Switch to using proper tooling for testing against golden data.
    float averagePixelAbsoluteDifference =
        getBitmapAveragePixelAbsoluteDifferenceArgb8888(expectedBitmap, actualBitmap, testId);
    assertThat(averagePixelAbsoluteDifference).isAtMost(MAXIMUM_AVERAGE_PIXEL_ABSOLUTE_DIFFERENCE);
  }

  @Test
  public void drawFrame_changeAspectRatio_scaleToFit_wide_matchesGoldenFile() throws Exception {
    String testId = "drawFrame_changeAspectRatio_scaleToFit_wide";
    presentationShaderProgram =
        Presentation.createForAspectRatio(/* aspectRatio= */ 2f, Presentation.LAYOUT_SCALE_TO_FIT)
            .toGlShaderProgram(context, /* useHdr= */ false);
    Size outputSize = presentationShaderProgram.configure(inputWidth, inputHeight);
    setupOutputTexture(outputSize.getWidth(), outputSize.getHeight());
    Bitmap expectedBitmap = readBitmap(ASPECT_RATIO_SCALE_TO_FIT_WIDE_PNG_ASSET_PATH);

    presentationShaderProgram.drawFrame(inputTexId, /* presentationTimeUs= */ 0);
    Bitmap actualBitmap =
        createArgb8888BitmapFromCurrentGlFramebuffer(outputSize.getWidth(), outputSize.getHeight());

    maybeSaveTestBitmapToCacheDirectory(testId, /* bitmapLabel= */ "actual", actualBitmap);
    // TODO(b/207848601): Switch to using proper tooling for testing against golden data.
    float averagePixelAbsoluteDifference =
        getBitmapAveragePixelAbsoluteDifferenceArgb8888(expectedBitmap, actualBitmap, testId);
    assertThat(averagePixelAbsoluteDifference).isAtMost(MAXIMUM_AVERAGE_PIXEL_ABSOLUTE_DIFFERENCE);
  }

  @Test
  public void drawFrame_changeAspectRatio_scaleToFitWithCrop_narrow_matchesGoldenFile()
      throws Exception {
    String testId = "drawFrame_changeAspectRatio_scaleToFitWithCrop_narrow";
    presentationShaderProgram =
        Presentation.createForAspectRatio(
                /* aspectRatio= */ 1f, Presentation.LAYOUT_SCALE_TO_FIT_WITH_CROP)
            .toGlShaderProgram(context, /* useHdr= */ false);
    Size outputSize = presentationShaderProgram.configure(inputWidth, inputHeight);
    setupOutputTexture(outputSize.getWidth(), outputSize.getHeight());
    Bitmap expectedBitmap = readBitmap(ASPECT_RATIO_SCALE_TO_FIT_WITH_CROP_NARROW_PNG_ASSET_PATH);

    presentationShaderProgram.drawFrame(inputTexId, /* presentationTimeUs= */ 0);
    Bitmap actualBitmap =
        createArgb8888BitmapFromCurrentGlFramebuffer(outputSize.getWidth(), outputSize.getHeight());

    maybeSaveTestBitmapToCacheDirectory(testId, /* bitmapLabel= */ "actual", actualBitmap);
    // TODO(b/207848601): Switch to using proper tooling for testing against golden data.
    float averagePixelAbsoluteDifference =
        getBitmapAveragePixelAbsoluteDifferenceArgb8888(expectedBitmap, actualBitmap, testId);
    assertThat(averagePixelAbsoluteDifference).isAtMost(MAXIMUM_AVERAGE_PIXEL_ABSOLUTE_DIFFERENCE);
  }

  @Test
  public void drawFrame_changeAspectRatio_scaleToFitWithCrop_wide_matchesGoldenFile()
      throws Exception {
    String testId = "drawFrame_changeAspectRatio_scaleToFitWithCrop_wide";
    presentationShaderProgram =
        Presentation.createForAspectRatio(
                /* aspectRatio= */ 2f, Presentation.LAYOUT_SCALE_TO_FIT_WITH_CROP)
            .toGlShaderProgram(context, /* useHdr= */ false);
    Size outputSize = presentationShaderProgram.configure(inputWidth, inputHeight);
    setupOutputTexture(outputSize.getWidth(), outputSize.getHeight());
    Bitmap expectedBitmap = readBitmap(ASPECT_RATIO_SCALE_TO_FIT_WITH_CROP_WIDE_PNG_ASSET_PATH);

    presentationShaderProgram.drawFrame(inputTexId, /* presentationTimeUs= */ 0);
    Bitmap actualBitmap =
        createArgb8888BitmapFromCurrentGlFramebuffer(outputSize.getWidth(), outputSize.getHeight());

    maybeSaveTestBitmapToCacheDirectory(testId, /* bitmapLabel= */ "actual", actualBitmap);
    // TODO(b/207848601): Switch to using proper tooling for testing against golden data.
    float averagePixelAbsoluteDifference =
        getBitmapAveragePixelAbsoluteDifferenceArgb8888(expectedBitmap, actualBitmap, testId);
    assertThat(averagePixelAbsoluteDifference).isAtMost(MAXIMUM_AVERAGE_PIXEL_ABSOLUTE_DIFFERENCE);
  }

  @Test
  public void drawFrame_changeAspectRatio_stretchToFit_narrow_matchesGoldenFile() throws Exception {
    String testId = "drawFrame_changeAspectRatio_stretchToFit_narrow";
    presentationShaderProgram =
        Presentation.createForAspectRatio(/* aspectRatio= */ 1f, Presentation.LAYOUT_STRETCH_TO_FIT)
            .toGlShaderProgram(context, /* useHdr= */ false);
    Size outputSize = presentationShaderProgram.configure(inputWidth, inputHeight);
    setupOutputTexture(outputSize.getWidth(), outputSize.getHeight());
    Bitmap expectedBitmap = readBitmap(ASPECT_RATIO_STRETCH_TO_FIT_NARROW_PNG_ASSET_PATH);

    presentationShaderProgram.drawFrame(inputTexId, /* presentationTimeUs= */ 0);
    Bitmap actualBitmap =
        createArgb8888BitmapFromCurrentGlFramebuffer(outputSize.getWidth(), outputSize.getHeight());

    maybeSaveTestBitmapToCacheDirectory(testId, /* bitmapLabel= */ "actual", actualBitmap);
    // TODO(b/207848601): Switch to using proper tooling for testing against golden data.
    float averagePixelAbsoluteDifference =
        getBitmapAveragePixelAbsoluteDifferenceArgb8888(expectedBitmap, actualBitmap, testId);
    assertThat(averagePixelAbsoluteDifference).isAtMost(MAXIMUM_AVERAGE_PIXEL_ABSOLUTE_DIFFERENCE);
  }

  @Test
  public void drawFrame_changeAspectRatio_stretchToFit_wide_matchesGoldenFile() throws Exception {
    String testId = "drawFrame_changeAspectRatio_stretchToFit_wide";
    presentationShaderProgram =
        Presentation.createForAspectRatio(/* aspectRatio= */ 2f, Presentation.LAYOUT_STRETCH_TO_FIT)
            .toGlShaderProgram(context, /* useHdr= */ false);
    Size outputSize = presentationShaderProgram.configure(inputWidth, inputHeight);
    setupOutputTexture(outputSize.getWidth(), outputSize.getHeight());
    Bitmap expectedBitmap = readBitmap(ASPECT_RATIO_STRETCH_TO_FIT_WIDE_PNG_ASSET_PATH);

    presentationShaderProgram.drawFrame(inputTexId, /* presentationTimeUs= */ 0);
    Bitmap actualBitmap =
        createArgb8888BitmapFromCurrentGlFramebuffer(outputSize.getWidth(), outputSize.getHeight());

    maybeSaveTestBitmapToCacheDirectory(testId, /* bitmapLabel= */ "actual", actualBitmap);
    // TODO(b/207848601): Switch to using proper tooling for testing against golden data.
    float averagePixelAbsoluteDifference =
        getBitmapAveragePixelAbsoluteDifferenceArgb8888(expectedBitmap, actualBitmap, testId);
    assertThat(averagePixelAbsoluteDifference).isAtMost(MAXIMUM_AVERAGE_PIXEL_ABSOLUTE_DIFFERENCE);
  }

  private void setupOutputTexture(int outputWidth, int outputHeight) throws GlUtil.GlException {
    int outputTexId =
        GlUtil.createTexture(
            outputWidth, outputHeight, /* useHighPrecisionColorComponents= */ false);
    int frameBuffer = GlUtil.createFboForTexture(outputTexId);
    GlUtil.focusFramebuffer(
        checkNotNull(eglDisplay),
        checkNotNull(eglContext),
        checkNotNull(placeholderEglSurface),
        frameBuffer,
        outputWidth,
        outputHeight);
  }
}
