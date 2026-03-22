package ru.zabkli.ui.news

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.http.SslError
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.fragment.app.Fragment
import ru.zabkli.R
import java.io.ByteArrayInputStream

class NewsFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? = inflater.inflate(R.layout.fragment_news, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val webView = view.findViewById<WebView>(R.id.webview)
        val noInternetError = view.findViewById<LinearLayout>(R.id.error_message)

        if (networkState()) {
            noInternetError.visibility = View.GONE
            webView.visibility = View.VISIBLE
            createWebView(webView)
            // Открываем сайт с новостями
            webView.loadUrl("https://zabkli.gosuslugi.ru/roditelyam-i-uchenikam/novosti/")
        } else {
            webView.visibility = View.GONE
            noInternetError.visibility = View.VISIBLE
        }
    }

    private fun networkState(): Boolean {
        val connectivityManager = requireContext().getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork
        val capabilities = connectivityManager.getNetworkCapabilities(network)
        return capabilities != null && (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET))
    }

    private fun createWebView(webView: WebView) {
        // Чтобы всё нормально и быстро грузилось
        with(webView.settings) {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            cacheMode = WebSettings.LOAD_DEFAULT

            offscreenPreRaster = true
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE

            // сперва блокируем изображения, чтобы ускорить показ основного контента
            loadsImagesAutomatically = false
            useWideViewPort = true
        }

        // Аппаратный рендер
        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null)

        // Подавляем/обрабатываем консольные сообщения (уберём шум в логах)
        webView.webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                if (consoleMessage == null) return true
                // Непустые ошибки логируем
                return when (consoleMessage.messageLevel()) {
                    ConsoleMessage.MessageLevel.ERROR -> {
                        Log.e(
                            "WebViewConsole",
                            "${consoleMessage.message()} -- ${consoleMessage.sourceId()}:${consoleMessage.lineNumber()}"
                        )
                        true
                    }
                    else -> true
                }
            }
        }

        // Кастомный WebViewClient: включение изображений после загрузки
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                // Включаем загрузку изображений после того, как основной HTML/JS отработал
                view?.settings?.loadsImagesAutomatically = true
            }

            override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: SslError?) {
                // По умолчанию — отменяем загрузку при ошибке
                handler?.cancel()
            }
        }
    }
}