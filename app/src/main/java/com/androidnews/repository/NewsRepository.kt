package com.androidnews.repository

import androidx.annotation.WorkerThread
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.androidnews.Config.pageSize
import com.androidnews.data.Article
import com.androidnews.data.ArticlePage
import com.androidnews.repository.db.AppDatabase
import com.androidnews.repository.db.ArticleDao
import com.androidnews.repository.service.NewsService
import com.androidnews.utils.toEpochDateString
import com.androidnews.utils.toMD5String
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.schedulers.Schedulers
import timber.log.Timber
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton


@Singleton
class NewsRepository @Inject
constructor(private val newsService: NewsService, private val appDatabase: AppDatabase) {

    val articlePage: MutableLiveData<Result<ArticlePage>> by lazy {
        MutableLiveData<Result<ArticlePage>>()
    }


    val articleDao: ArticleDao
        get() {
            return appDatabase.articleDao()
        }

    val disposable = CompositeDisposable()

    init {

    }


    fun getArticlePage(query: String, page: Int): LiveData<Result<ArticlePage>> {
        fetchArticlePage(query, page)
        return articlePage;
    }


    private fun fetchArticlePage(query: String, page: Int) {
        Timber.v("fetchArticlePage: query:$query, page:$page")
        val pageSize = pageSize

        articlePage.postFetching(true)
        disposable.add(
            newsService.getArticles(query, page = page, pageSize = pageSize)
                .subscribeOn(Schedulers.io())
                .observeOn(Schedulers.io())
                .subscribe(
                    { response ->

                        response.articlesList?.forEach {
                            Timber.v("fetchArticlePage: page:${page}, title:${it.title}")
                        }
                        // this is in background thread
                        val fetchedData = response.toArticleList(query, page, pageSize)

                        Timber.v("fetchArticlePage:(success response) query:$query, page:${fetchedData.pageNum} totalPage:${fetchedData.totalPages} totalItems=${fetchedData.totalItems}")


                        // put fetched data inside db
                        putInDb(fetchedData)
                        val mergedData = articlePage.value?.data?.let { existingData ->
                            if (existingData.isAppendingPossible(fetchedData)) {
                                existingData.apply {
                                    append(fetchedData)
                                }
                            } else {
                                existingData
                            }
                        } ?: fetchedData

                        articlePage.postValue(Result(dataSource = DataSource.Api, data = mergedData))
                    },
                    { e ->
                        // this is in background thread
                        // try to read from db
                        val result = getFromDb(query, pageSize)
                        // var result = Pair(listOf<Article>(), 0)
                        Timber.v("fetchArticlePage: Error , e:${e}")
                        Timber.v("fetchArticlePage:(getFromDb) result size :${result.first.size}, count:${result.second}")
                        if (result.first.isNotEmpty()) {

                            // there is result
                            val cachedPage = ArticlePage(
                                query = query,
                                pageSize = pageSize,
                                list = result.first,
                                pageNum = 0,
                                totalItems = result.second
                            )
                            articlePage.postValue(Result(dataSource = DataSource.Db, data = cachedPage))
                        } else {

                            // no result
                            articlePage.postValue(Result(dataSource = DataSource.Api, error = e))
                        }
                    })
        )
    }


    /**
     * insert [Article] objects to db
     */
    @WorkerThread
    private fun putInDb(articlePage: ArticlePage) {
        articlePage.list.map {
            it.apply {
                query = articlePage.query

                // generate a unique key for this article object
                id = generateKey(
                    title,
                    source?.id,
                    source?.name,
                    publishedAt?.toEpochDateString()
                )
            }
        }
        articleDao.putAll(*articlePage.list.toTypedArray())
    }


    /**
     * Return a [List] of [Article] (size [pageSize]) after the last item of the existing list
     *
     * Or just a [List] from current time (size [pageSize])
     *
     * The total count will also be returned
     *
     * Sort by [Article.publishedAt]
     */
    @WorkerThread
    private fun getFromDb(query: String, pageSize: Int): Pair<List<Article>, Int> {
        val existingList = articlePage.value?.data?.list
        val query = { publishedAfter: Date ->
            val list = (existingList ?: listOf()).toMutableList().apply {
                addAll(articleDao.query(query, publishedAfter.time, pageSize))
            }

            val count = articleDao.getQueryCount(query, publishedAfter.time)
            Pair(list, count)
        }

        val lastArticleDate = existingList?.last()?.publishedAt ?: Date()
        return when (articlePage.value?.dataSource) {
            DataSource.Api -> {
                query(Date())
            }

            DataSource.Db -> {
                query(lastArticleDate)
            }
            else -> {
                query(lastArticleDate)
            }
        }
    }

}


internal fun <K> MutableLiveData<Result<K>>.postFetching(fetching: Boolean) {
    this.value?.let { existing ->
        this.postValue(existing.also {
            it.fetching = fetching
        })
    }
}


internal fun generateKey(vararg args: String?): String {
    return StringBuilder().also { sb ->
        args.forEach { arg ->
            sb.append("${arg ?: ""}/")
        }
    }.toString().toMD5String()
}