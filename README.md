<!--
  title: Elasticsearch term position similarity
  description: Плагин для релевантности относительно положения в строке
  author: Roman Saritskiy
  -->

./gradlew clean assemble

Документация по установке расположена по адресу https://www.elastic.co/guide/en/elasticsearch/plugins/current/plugin-management.html

Плагин позволяет использовать релевантность относительно начала строки. Чем ближе к началу строки тем релевантнее.

Использование

```json
{  
   "query":{  
      "position_match":{  
         "query":{  
            "match_phrase":{  
               "productName":{  
                  "query":"Зубная паста",
                  "slop":50,
                  "_name":"strictInProductName"
               }
            }
         },
         "boost":10
      }
   }
}
```

### Исходные данные 

|  productName |
| ------------ |
|   Зубная щетка президент + зубная паста |
|   Зубная паста лакалют |

### Результат

|  productName | Position in string |
| ------------ |------|
|   Зубная паста лакалют | 1 |
|   Зубная щетка президент + зубная паста | 4 |


Внимание. Работает и протестирован на весиии 7.0.0
Чтобы собрать плагин под более позднюю версию измените версию в файле VERSION.txt