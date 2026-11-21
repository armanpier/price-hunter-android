package com.teamwork.pricehunter.core.intelligence

import org.junit.Assert.*
import org.junit.Test

class QueryRelevanceEvaluatorTest {

    @Test
    fun test_unrelated_items_for_air_conditioner_query_are_rejected() {
        val query = "کولر گازی پرتابل"

        // Irrelevant items that SnappShop returned in the user's test
        assertFalse(
            "Omega 3 softgel must be rejected for AC query",
            QueryRelevanceEvaluator.isRelevant("سافت ژل امگا 3 ویتامینز 1000 میلی گرمی نکستایل بسته 60 عددی", query)
        )
        assertFalse(
            "Hair loss pill must be rejected for AC query",
            QueryRelevanceEvaluator.isRelevant("قرص مکمل جلوگیری از ریزش موی فولیکوژن شرکت اروند فارمد بسته 60 عددی", query)
        )
        assertFalse(
            "Gold coin must be rejected for AC query",
            QueryRelevanceEvaluator.isRelevant("سکه گرمی طلا 18 عیار پارسیان امین زر 003", query)
        )
    }

    @Test
    fun test_relevant_items_for_air_conditioner_query_are_accepted() {
        val query = "کولر گازی پرتابل"

        assertTrue(
            QueryRelevanceEvaluator.isRelevant("کولر گازی پرتابل 12000 ایوولی مدل EVPR-12K-PO", query)
        )
        assertTrue(
            QueryRelevanceEvaluator.isRelevant("کولر آبی پرتابل برفاب مدل BF3-Z", query)
        )
        assertTrue(
            QueryRelevanceEvaluator.isRelevant("کولر گازی جنرال گلد 12000 پرتابل", query)
        )
    }

    @Test
    fun test_phone_and_laptop_queries() {
        assertTrue(
            QueryRelevanceEvaluator.isRelevant("گوشی موبایل سامسونگ Galaxy S24 Ultra", "گوشی سامسونگ s24")
        )
        assertTrue(
            QueryRelevanceEvaluator.isRelevant("لپ تاپ 15.6 اینچی ایسوس مدل Vivobook 15", "لپ تاپ ایسوس")
        )
        assertFalse(
            QueryRelevanceEvaluator.isRelevant("شامپو ضد شوره کلیر", "لپ تاپ ایسوس")
        )
    }

    @Test
    fun test_single_token_query() {
        assertTrue(QueryRelevanceEvaluator.isRelevant("ساندویچ کالباس گوشت 90 درصد", "ساندویچ"))
        assertFalse(QueryRelevanceEvaluator.isRelevant("پیتزا قارچ و گوشت دونفره", "ساندویچ"))
    }
}
