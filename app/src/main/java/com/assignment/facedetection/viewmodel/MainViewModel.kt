package com.assignment.facedetection.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor() : ViewModel() {
    private val _captureLiveData = MutableLiveData<Boolean>()
    val captureLiveData: LiveData<Boolean>
        get() = _captureLiveData

    fun captureImage(){
        _captureLiveData.postValue(true)
    }
}