package com.teamwork.pricehunter.e2e.mock

object ProviderFixtures {

    const val DIGIKALA_SAMSUNG_RESPONSE = """
{
  "status": 200,
  "data": {
    "widgets": [
      {
        "type": "vertical_product_listing",
        "data": {
          "widgets": [
            {
              "type": "product",
              "data": {
                "id": 14285701,
                "title_fa": "گوشی موبایل سامسونگ مدل Galaxy S24 Ultra دو سیم کارت ظرفیت 256 گیگابایت",
                "status": "marketable",
                "images": {
                  "main": {
                    "url": ["https://dkstatics-public.digikala.com/digikala-products/s24ultra.jpg"]
                  }
                },
                "rating": {
                  "rate": 92.0,
                  "count": 450
                },
                "default_variant": {
                  "id": 998811,
                  "marketable_stock": 14,
                  "seller": {
                    "id": 1,
                    "title": "دیجی‌کالا",
                    "code": "DK"
                  },
                  "price": {
                    "selling_price": 685000000,
                    "rrp_price": 720000000,
                    "discount_percent": 5
                  }
                }
              }
            },
            {
              "type": "product",
              "data": {
                "id": 14285702,
                "title_fa": "کاور مدل سیلیکونی مناسب برای گوشی موبایل سامسونگ Galaxy S24 Ultra",
                "status": "marketable",
                "images": {
                  "main": {
                    "url": ["https://dkstatics-public.digikala.com/digikala-products/case_s24.jpg"]
                  }
                },
                "rating": {
                  "rate": 84.0,
                  "count": 120
                },
                "default_variant": {
                  "id": 998812,
                  "marketable_stock": 50,
                  "seller": {
                    "id": 402,
                    "title": "لوازم جانبی برتر",
                    "code": "ACC"
                  },
                  "price": {
                    "selling_price": 2500000,
                    "rrp_price": 3000000,
                    "discount_percent": 16
                  }
                }
              }
            }
          ]
        }
      }
    ]
  }
}
"""

    const val DIGIKALA_DAIRY_RESPONSE = """
{
  "status": 200,
  "data": {
    "widgets": [
      {
        "type": "vertical_product_listing",
        "data": {
          "widgets": [
            {
              "type": "product",
              "data": {
                "id": 883100,
                "title_fa": "شیر کم چرب کاله ۲۰۰ میلی‌لیتر بسته ۶ عددی",
                "status": "marketable",
                "images": {
                  "main": {
                    "url": ["https://dkstatics-public.digikala.com/digikala-products/milk_6pack.jpg"]
                  }
                },
                "rating": { "rate": 90.0, "count": 65 },
                "default_variant": {
                  "id": 33221,
                  "marketable_stock": 30,
                  "seller": { "id": 1, "title": "دیجی‌کالا جت" },
                  "price": {
                    "selling_price": 1200000,
                    "rrp_price": 1200000,
                    "discount_percent": 0
                  }
                }
              }
            }
          ]
        }
      }
    ]
  }
}
"""

    const val DIGIKALA_SCHEMA_DRIFT_RESPONSE = """
{
  "status": 200,
  "data": {
    "products_v3": [
      {
        "item_id": 99999,
        "name_persian": "گوشی موبایل سامسونگ",
        "new_price_structure": {
          "irt_price": 65000000
        }
      }
    ],
    "future_experimental_flag": true
  }
}
"""

    const val TOROB_IPHONE_SEARCH_RESPONSE = """
{
  "count": 2,
  "results": [
    {
      "random_key": "a60ad801-9e34-4a09-bea2-7309eefb6e72",
      "name1": "گوشی اپل مدل iPhone 13 تک سیم‌کارت با ظرفیت 128 گیگابایت",
      "name2": "Apple iPhone 13 Single Sim 128GB",
      "price": 61500000,
      "price_text": "از ۶۱٫۵۰۰٫۰۰۰ تومان",
      "shop_text": "در ۱۴۴ فروشگاه",
      "image_url": "https://image.torob.com/base/images/iphone13.jpg"
    },
    {
      "random_key": "c11bd902-8f12-4b10-aca3-8109eefb6f99",
      "name1": "قاب محافظ ضد ضربه مناسب برای گوشی آیفون 13",
      "name2": "Shockproof Case for iPhone 13",
      "price": 180000,
      "price_text": "۱۸۰٫۰۰۰ تومان",
      "shop_text": "در ۱۲ فروشگاه",
      "image_url": "https://image.torob.com/base/images/case13.jpg"
    }
  ]
}
"""

    const val TOROB_SELLERS_RESPONSE = """
{
  "count": 2,
  "results": [
    {
      "name": "فروشگاه تکنولوژی برتر",
      "price": 61500000,
      "price_text": "۶۱٫۵۰۰٫۰۰۰ تومان",
      "shop_score": 4.8,
      "page_url": "https://torob.com/shop/123/product/456"
    },
    {
      "name": "دیجی آنلاین",
      "price": 61900000,
      "price_text": "۶۱٫۹۰۰٫۰۰۰ تومان",
      "shop_score": 4.5,
      "page_url": "https://torob.com/shop/124/product/457"
    }
  ]
}
"""

    const val TOROB_WAF_490_HTML = """
<!DOCTYPE html>
<html lang="fa" dir="rtl">
<head>
  <meta charset="utf-8">
  <title>کد امنیتی ترب - ارکپچا</title>
</head>
<body>
  <div class="captcha-box">
    <h2>فعالیت غیرعادی از سمت شبکه شما شناسایی شده است</h2>
    <p>لطفاً جهت ادامه، تست کپچا را تکمیل کنید.</p>
    <div id="arcaptcha-widget" data-site-key="torob-prod-key"></div>
  </div>
</body>
</html>
"""

    const val SNAPPSHOP_SEARCH_RESPONSE = """
{
  "data": {
    "structure": [
      {
        "section_type": "plp",
        "items": [
          {
            "id": "pn-98214",
            "title": "گوشی موبایل سامسونگ مدل Galaxy S24 Ultra ظرفیت 256 گیگابایت",
            "price": {
              "price": 71000000,
              "discounted_price": 67900000,
              "discount": 4
            },
            "state": {
              "rate": 4.6,
              "rate_count": 82
            },
            "image": "https://images.snappshop.ir/product/s24ultra.jpg",
            "href": "/product/snp-98214/samsung-s24-ultra"
          }
        ]
      }
    ]
  }
}
"""

    const val SNAPPSHOP_SOTOON_403_HTML = """
<!DOCTYPE html>
<html>
<head><title>403 Access Denied</title></head>
<body>
<h1>403 Forbidden - Sotoon CDN</h1>
<p>دسترسی شما از این آدرس IP مسدود است. لطفاً فیلترشکن خود را خاموش کرده و مجدداً تلاش نمایید.</p>
</body>
</html>
"""

    const val SNAPPFOOD_VENDORS_RESPONSE = """
{
  "status": true,
  "data": {
    "finalResult": [
      {
        "type": "VENDOR",
        "data": {
          "code": "947evd",
          "title": "رستوران نایب (شعبه سعادت آباد)",
          "lat": 35.77331,
          "lon": 51.41859,
          "rating": 8.8,
          "isOpen": true,
          "deliveryFee": 35000,
          "minOrder": 150000,
          "address": "سعادت آباد، میدان کاج"
        }
      }
    ]
  }
}
"""

    const val SNAPPFOOD_DISHES_RESPONSE = """
{
  "status": true,
  "data": {
    "product_variations": {
      "items": [
        {
          "id": 55102,
          "title": "چلوکباب کوبیده زعفرانی مخصوص",
          "price": 280000,
          "discountRatio": 10,
          "priceAfterDiscount": 252000,
          "vendor": {
            "code": "947evd",
            "title": "رستوران نایب (شعبه سعادت آباد)",
            "lat": 35.77331,
            "lon": 51.41859,
            "deliveryFee": 35000,
            "minOrder": 150000
          }
        }
      ]
    }
  }
}
"""

    const val SNAPPFOOD_STATE_RESPONSE = """
{
  "status": true,
  "data": {
    "delivery": {
      "fee": 35000,
      "discount": 5000
    },
    "service_fee": 0,
    "packaging_fee": 15000,
    "tax_percent": 10,
    "minOrder": 150000
  }
}
"""

    const val SNAPPFOOD_MENU_RESPONSE = """
{
  "status": true,
  "data": {
    "vendor": {
      "title": "رستوران نایب (شعبه سعادت آباد)",
      "containerPrice": 15000
    }
  }
}
"""

    const val TAPSIFOOD_DISHES_RESPONSE = """
{
  "status": "SUCCESS",
  "data": {
    "products": [
      {
        "id": "item-991",
        "title": "چلو کباب کوبیده ممتاز",
        "basePrice": 275000,
        "discountPrice": 250000,
        "vendor": {
          "vendorCode": "nayeb-saadatabad",
          "vendorTitle": "نایب سعادت آباد",
          "latitude": 35.77310,
          "longitude": 51.41840
        }
      }
    ]
  }
}
"""

    const val TAPSIFOOD_VENDORS_RESPONSE = """
{
  "status": "SUCCESS",
  "data": {
    "vendors": [
      {
        "vendorCode": "nayeb-saadatabad",
        "vendorTitle": "نایب سعادت آباد",
        "latitude": 35.77310,
        "longitude": 51.41840,
        "isOpen": true
      }
    ]
  }
}
"""

    const val TAPSIFOOD_CART_VALIDATE_RESPONSE = """
{
  "status": "SUCCESS",
  "data": {
    "vendor": {
      "vendorCode": "nayeb-saadatabad",
      "vendorTitle": "نایب سعادت آباد",
      "latitude": 35.77310,
      "longitude": 51.41840
    },
    "items": [
      {
        "id": "item-991",
        "title": "چلو کباب کوبیده ممتاز",
        "basePrice": 275000,
        "discountPrice": 250000,
        "packagingFee": 12000
      }
    ],
    "quote": {
      "subtotal": 250000,
      "packagingFee": 12000,
      "deliveryFee": 30000,
      "serviceFee": 5000,
      "tax": 29700,
      "totalDeliveredCost": 326700,
      "minOrderThreshold": 120000
    }
  }
}
"""

    const val OKALA_SEARCH_RESPONSE = """
{
  "status": 200,
  "data": [
    {
      "id": "okala-101",
      "title": "شیر کم چرب کاله ۱ لیتر",
      "price": 35000,
      "available": true,
      "url": "https://okala.com/product/101"
    }
  ]
}
"""

    const val REMOTE_CONFIG_JSON = """
{
  "version": 2,
  "providers": {
    "digikala": {
      "id": "digikala",
      "enabled": true,
      "timeoutMs": 10000,
      "baseUrl": "https://api.digikala.com/discovery/api/v2/search",
      "extraHeaders": {}
    },
    "torob": {
      "id": "torob",
      "enabled": true,
      "timeoutMs": 10000,
      "baseUrl": "https://api.torob.com/v4/base-product/search/",
      "extraHeaders": {}
    },
    "snappshop": {
      "id": "snappshop",
      "enabled": true,
      "timeoutMs": 10000,
      "baseUrl": "https://apix.snappshop.ir/search/v1",
      "extraHeaders": {
        "s-device": "DESKTOP",
        "s-device-source": "shop"
      }
    },
    "snappfood": {
      "id": "snappfood",
      "enabled": true,
      "timeoutMs": 12000,
      "baseUrl": "https://snappfood.ir/search/api/v5/search",
      "extraHeaders": {}
    },
    "tapsifood": {
      "id": "tapsifood",
      "enabled": true,
      "timeoutMs": 12000,
      "baseUrl": "https://api.tapsi.food/v1/api/search-discovery/Search/product-search",
      "extraHeaders": {}
    },
    "okala": {
      "id": "okala",
      "enabled": false,
      "timeoutMs": 10000,
      "baseUrl": "https://api.okala.com/search",
      "extraHeaders": {}
    }
  },
  "experimental_ai_v2": true
}
"""
}
