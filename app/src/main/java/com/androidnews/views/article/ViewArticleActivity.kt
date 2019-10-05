package com.androidnews.views.article

import android.os.Bundle
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.widget.Toolbar
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import com.androidnews.R
import com.androidnews.common.*
import com.androidnews.data.Article
import com.androidnews.viewmodel.ArticleViewModel
import com.androidnews.viewmodel.ViewModelFactory
import com.androidnews.views.EXTRA_ARTICLE
import com.google.android.material.floatingactionbutton.FloatingActionButton
import javax.inject.Inject

/**
 * A simple detail [Activity] that shows a [User] object (read-only)
 */
class ViewArticleActivity : BaseActivity() {
    @Inject
    lateinit var viewModelFactory: ViewModelFactory

    private val viewModel by lazy {
        ViewModelProviders.of(this, viewModelFactory).get(ArticleViewModel::class.java)
    }


    private val toolbarBackgroundImage by lazy {
        findViewById<ImageView>(R.id.imageview_toolbar_background)
    }

    private val toolbar by lazy {
        findViewById<Toolbar>(R.id.toolbar)
    }


    private val titleText by lazy {
        findViewById<TextView>(R.id.textview_article_title)
    }

    private val dateText by lazy {
        findViewById<TextView>(R.id.textview_article_publish_date)
    }


    private val authorText by lazy {
        findViewById<TextView>(R.id.textview_article_author)
    }


    private val contentText by lazy {
        findViewById<TextView>(R.id.textview_article_content)
    }


    private val fab by lazy {
        findViewById<FloatingActionButton>(R.id.fab)
    }

    private val article: Article?
        get() {
            return viewModel.viewingArticle.value
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_view_article)
        viewModel.viewingArticle.observe(this, Observer {
            update(it)
        })

        intent?.getJsonObject<Article>(json, EXTRA_ARTICLE)?.let {
            viewModel.viewingArticle.postValue(it)
        } ?: run {
            showSnackBar("Article object not found")
            finish()
            return
        }

        fab.setOnClickListener {
            article?.url?.let {
                startSystemBrowser(it)
            }
        }

    }

    private fun update(article: Article?) {
        article ?: return

        toolbar.title = article.source?.name ?: ""

        article.urlToImage?.let {
            toolbarBackgroundImage.loadUrl(this, it)
        }
        article.publishedAt?.let {
            dateText.visible()
            dateText.text = it.getPastDurationText(this)

        } ?: run {
            dateText.gone()
        }

        article.author?.let {
            authorText.visible()
            authorText.text = it

        } ?: run {
            authorText.gone()
        }
        titleText.text = article.title
        contentText.text = article.content
    }


}