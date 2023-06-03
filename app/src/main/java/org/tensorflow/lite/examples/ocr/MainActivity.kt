/* Copyright 2021 The TensorFlow Authors. All Rights Reserved.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
==============================================================================
*/

package org.tensorflow.lite.examples.ocr

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.Switch
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory
import com.bumptech.glide.Glide
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import kotlinx.android.synthetic.main.tfe_is_activity_main.BSelectImage
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.Executors

private const val TAG = "MainActivity"

class MainActivity : AppCompatActivity() {

  private val galleryActivityResultLauncher = registerForActivityResult(
    ActivityResultContracts.StartActivityForResult()
  ) {
    if (it.resultCode == Activity.RESULT_OK){
      val data = it.data
      if (data != null && data.data != null){
        try{
          var selectedImageBitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(
            this.contentResolver, data.data!!
          ))
          selectedImageBitmap = selectedImageBitmap.copy(Bitmap.Config.ARGB_8888, true)
          previewImage.setImageBitmap(selectedImageBitmap)
          selectedImageName = selectedImageBitmap
        } catch (e: IOException) {
          Log.e(TAG, "Failed to open a test image")
        }
      }
    }
  }

//  private val tfImageName = "tensorflow.jpg"
//  private val androidImageName = "android.jpg"
//  private val chromeImageName = "chrome.jpg"
  private lateinit var viewModel: MLExecutionViewModel
  private lateinit var resultImageView: ImageView
//  private lateinit var tfImageView: ImageView
//  private lateinit var androidImageView: ImageView
//  private lateinit var chromeImageView: ImageView
  private lateinit var chipsGroup: ChipGroup
  private lateinit var runButton: Button
//  private lateinit var textPromptTextView: TextView
  private lateinit var bSelectImage: Button
  private lateinit var previewImage: ImageView
  private lateinit var resultTextView: TextView

  private var useGPU = false
  private lateinit var selectedImageName: Bitmap
  private var ocrModel: OCRModelExecutor? = null
  private val inferenceThread = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
  private val mainScope = MainScope()
  private val mutex = Mutex()

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.tfe_is_activity_main)

    val toolbar: Toolbar = findViewById(R.id.toolbar)
    setSupportActionBar(toolbar)
    supportActionBar?.setDisplayShowTitleEnabled(false)

//    tfImageView = findViewById(R.id.tf_imageview)
//    androidImageView = findViewById(R.id.android_imageview)
//    chromeImageView = findViewById(R.id.chrome_imageview)

    //val candidateImageViews = arrayOf<ImageView>(tfImageView, androidImageView, chromeImageView)

//    val assetManager = assets
//    try {
//      val tfInputStream: InputStream = assetManager.open(tfImageName)
//      val tfBitmap = BitmapFactory.decodeStream(tfInputStream)
//      tfImageView.setImageBitmap(tfBitmap)
//      val androidInputStream: InputStream = assetManager.open(androidImageName)
//      val androidBitmap = BitmapFactory.decodeStream(androidInputStream)
//      androidImageView.setImageBitmap(androidBitmap)
//      val chromeInputStream: InputStream = assetManager.open(chromeImageName)
//      val chromeBitmap = BitmapFactory.decodeStream(chromeInputStream)
//      chromeImageView.setImageBitmap(chromeBitmap)
//    } catch (e: IOException) {
//      Log.e(TAG, "Failed to open a test image")
//    }
//
//    for (iv in candidateImageViews) {
//      setInputImageViewListener(iv)
//    }

    resultImageView = findViewById(R.id.result_imageview)
    chipsGroup = findViewById(R.id.chips_group)
    //textPromptTextView = findViewById(R.id.text_prompt)
    val useGpuSwitch: Switch = findViewById(R.id.switch_use_gpu)



    bSelectImage = findViewById(R.id.BSelectImage)
    previewImage = findViewById(R.id.IVPreviewImage)
    bSelectImage.setOnClickListener { imageChooser() }
    resultTextView = findViewById(R.id.result_text)

    viewModel = AndroidViewModelFactory(application).create(MLExecutionViewModel::class.java)
    viewModel.resultingBitmap.observe(
      this,
      Observer { resultImage ->
        if (resultImage != null) {
          updateUIWithResults(resultImage)
        }
        enableControls(true)
      }
    )

    mainScope.async(inferenceThread) { createModelExecutor(useGPU) }

    useGpuSwitch.setOnCheckedChangeListener { _, isChecked ->
      useGPU = isChecked
      Log.d("fef", "switch switched")
      mainScope.async(inferenceThread) { createModelExecutor(useGPU) }
    }

    runButton = findViewById(R.id.rerun_button)
    runButton.setOnClickListener {
      Log.d("fef", "start $ocrModel")
      enableControls(false)

      mainScope.async(inferenceThread) {
        mutex.withLock {
          if (ocrModel != null) {
            Log.d("fef", "start $ocrModel")
            viewModel.onApplyModel(baseContext, selectedImageName, ocrModel, inferenceThread)
          } else {
            Log.d(
              TAG,
              "Skipping running OCR since the ocrModel has not been properly initialized ..."
            )
          }
        }
      }
    }

    setChipsToLogView(HashMap<String, Int>())
    enableControls(true)
  }

  private fun imageChooser(){
    val galleryIntent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
    galleryActivityResultLauncher.launch(galleryIntent)
  }

//  @SuppressLint("ClickableViewAccessibility")
//  private fun setInputImageViewListener(iv: ImageView) {
//    iv.setOnTouchListener(
//      object : View.OnTouchListener {
//        override fun onTouch(v: View, event: MotionEvent?): Boolean {
//          if (v.equals(tfImageView)) {
//            selectedImageName = tfImageName
//            textPromptTextView.setText(getResources().getString(R.string.tfe_using_first_image))
//          } else if (v.equals(androidImageView)) {
//            selectedImageName = androidImageName
//            textPromptTextView.setText(getResources().getString(R.string.tfe_using_second_image))
//          } else if (v.equals(chromeImageView)) {
//            selectedImageName = chromeImageName
//            textPromptTextView.setText(getResources().getString(R.string.tfe_using_third_image))
//          }
//          return false
//        }
//      }
//    )
//  }

  private suspend fun createModelExecutor(useGPU: Boolean) {
    Log.d("fef", "start createModelExecutor")
    mutex.withLock {
      if (ocrModel != null) {
        ocrModel!!.close()
        ocrModel = null
      }
      try {
        ocrModel = OCRModelExecutor(this, useGPU)
        Log.d("fef", "end createModelExecutor")
      } catch (e: Exception) {
        Log.e(TAG, "Fail to create OCRModelExecutor: ${e.message}")
        val logText: TextView = findViewById(R.id.log_view)
        logText.text = e.message
      }
    }
  }

  private fun setChipsToLogView(itemsFound: Map<String, Int>) {
    chipsGroup.removeAllViews()

    for ((word, color) in itemsFound) {
      val chip = Chip(this)
      chip.text = word
      chip.chipBackgroundColor = getColorStateListForChip(color)
      chip.isClickable = false
      chipsGroup.addView(chip)
    }
    val labelsFoundTextView: TextView = findViewById(R.id.tfe_is_labels_found)
    if (chipsGroup.childCount == 0) {
      labelsFoundTextView.text = getString(R.string.tfe_ocr_no_text_found)
    } else {
      labelsFoundTextView.text = getString(R.string.tfe_ocr_texts_found)
    }
    chipsGroup.parent.requestLayout()
  }

  private fun getColorStateListForChip(color: Int): ColorStateList {
    val states =
      arrayOf(
        intArrayOf(android.R.attr.state_enabled), // enabled
        intArrayOf(android.R.attr.state_pressed) // pressed
      )

    val colors = intArrayOf(color, color)
    return ColorStateList(states, colors)
  }

  private fun setImageView(imageView: ImageView, image: Bitmap) {
    Glide.with(baseContext).load(image).override(250, 250).fitCenter().into(imageView)
  }

  private fun updateUIWithResults(modelExecutionResult: ModelExecutionResult) {
    setImageView(resultImageView, modelExecutionResult.bitmapResult)
    val logText: TextView = findViewById(R.id.log_view)
    logText.text = modelExecutionResult.executionLog
    //////////////////////////////////////////////////////////
    resultTextView.text = modelExecutionResult.resText
    //////////////////////////////////////////////////////////

    setChipsToLogView(modelExecutionResult.itemsFound)
    enableControls(true)
  }

  private fun enableControls(enable: Boolean) {
    runButton.isEnabled = enable
  }
}
