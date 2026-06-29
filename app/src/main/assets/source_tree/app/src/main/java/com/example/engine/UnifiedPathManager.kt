package com.example.engine

import android.content.Context
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer

object UnifiedPathManager {
    private val _activePath = MutableLiveData<String>()
    val activePath: LiveData<String> get() = _activePath

    private var appContext: Context? = null

    fun init(context: Context) {
        val app = context.applicationContext
        appContext = app
        val savedPath = ProjectContextManager.getCurrentProjectPath(app)
        if (_activePath.value != savedPath) {
            if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) {
                _activePath.value = savedPath
            } else {
                _activePath.postValue(savedPath)
            }
        }
    }

    fun getActivePath(context: Context): String {
        if (appContext == null) {
            init(context)
        }
        return _activePath.value ?: ProjectContextManager.getCurrentProjectPath(context)
    }

    fun setActivePath(path: String) {
        val ctx = appContext
        if (ctx != null) {
            ProjectContextManager.setCurrentProjectPath(ctx, path)
            ProjectContextManager.reloadActiveTemplateKeywords(ctx, path)
        }
        if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) {
            _activePath.value = path
        } else {
            _activePath.postValue(path)
        }
    }

    fun setActivePath(context: Context, path: String) {
        init(context)
        ProjectContextManager.setCurrentProjectPath(context, path)
        ProjectContextManager.reloadActiveTemplateKeywords(context, path)
        if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) {
            _activePath.value = path
        } else {
            _activePath.postValue(path)
        }
    }

    fun observeActivePath(owner: LifecycleOwner, observer: Observer<String>) {
        _activePath.observe(owner, observer)
    }
}
