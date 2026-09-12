# OpenSearch 3.0.0 — Developer Notes

## 1. Dependencies & Configuration

Add these two dependencies to your `pom.xml`:

```xml
<dependency>
    <groupId>org.opensearch.client</groupId>
    <artifactId>opensearch-java</artifactId>
</dependency>

<dependency>
    <groupId>org.apache.httpcomponents.client5</groupId>
    <artifactId>httpclient5</artifactId>
</dependency>
```

**Config properties you'll need:**

| Property | Type |
|---|---|
| `host` | `String` |
| `port` | `String` |
| `ssl` | `boolean` |
| `username` / `password` | `String` |
| `maxConnectionTotal` | `int` |
| `maxConnectionPerRoute` | `int` |
| `connectTimeoutSeconds` | `int` |
| `responseTimeoutSeconds` | `int` |

---

## 2. Config the `OpenSearchClient` Bean

Here's how the client gets assembled step by step:

- **Scheme** — resolve to `https` or `http` based on the `ssl` flag
- **HttpHost** — constructed from scheme + host + port
- **SSLContext** — use `loadTrustMaterial(null, (a, b) -> true)` to trust all certs (fine for internal/dev environments)
- **ApacheHttpClient5Transport** — this is the main piece:
    - **Mapper** → `JacksonJsonpMapper`
    - **HttpClientConfigCallback**:
        - Set up `BasicCredentialsProvider` with `UsernamePasswordCredentials` scoped to the host
    - **TlsStrategy** → `ClientTlsStrategyBuilder` with the `SSLContext` above + `NoopHostnameVerifier.INSTANCE`
    - **ConnectionManager** → `PoolingAsyncClientConnectionManagerBuilder` wired with:
        - the TLS strategy
        - `maxConnTotal` / `maxConnPerRoute` from config
        - `defaultConnectionConfig` for connect timeout
    - **HttpClient** → return the builder with credentials provider, connection manager, and `defaultRequestConfig` carrying the response timeout
- Finally, wrap everything in `new OpenSearchClient(transport)`

---

## 3. Limitations & Gotchas

### 3.1 Scoring & Custom Search Algorithms

**Context:** The requirements called for a custom scoring algorithm alongside fuzzy search or others.

The problem is that fuzzy search (and most other search modes) use **BM25 by default**, which factors in term frequency and document length — not what you want when the expectation is pure character-distance scoring (Levenshtein). Results end up ranked in ways that feel wrong.

The fix is to take control of scoring with a **Painless script score query**, so you can implement whatever algorithm you actually need.

OpenSearch has no built-in way to normalize matching scores into a fixed range like [0, 1] — you have to handle that yourself.

---

### 3.2 Painless Script Quirks

Painless is *mostly* Java-like, but it'll trip you up in a couple of ways:

- **No function definitions** — you can't define reusable functions. The workaround is a bit ugly: abuse a `for` loop that never executes to simulate a scoped block:
```painless
  for (int _ = 0; _ > 0; _++) { /* "function" body here */ }
```
- **Missing standard library methods** — some basic Java utility methods just don't exist. You'll find out at runtime.

---

### 3.3 Fuzzy Search Result Bleeding

Because fuzzy search scores results via BM25, you'll occasionally get documents back that don't actually match your input string by any reasonable measure. You need to add a **script-based filter** alongside your script score to drop results that fall below your own matching threshold.

---

### 3.4 Combining Multiple Search Strategies

If you need pagination across results from fuzzy, synonym, and other search strategies in a **single API call**, you have no choice but to merge everything into one compound query. There's no way to call each strategy separately and stitch results together while keeping pagination correct — it all has to go into one OpenSearch request.

---

### 3.5 Exception Handling

OpenSearch isn't very expressive with its error responses — almost everything that goes wrong comes back as a `400` or `500`. You have to **parse the error message** to figure out what actually happened. Don't expect distinct exception types for distinct failure scenarios.

---

## 4. Implementation

- Equal =:
    - Code: BoolQuery.Builder.must(TermQuery)
    - Query: query -> bool -> must -> term
- Not Equal !=:
    - Code: BoolQuery.Builder.mustNot(TermQuery)
    - Query: query -> bool -> must_not -> term
- Exist:
    - Code: BoolQuery.Builder.must(Query.exist(ExistQuery))
    - Query: query -> bool -> must -> exist
- Not Exist (value = null)
    - Code: BoolQuery.Builder.mustNot(Query.exist(ExistQuery))
    - Query: query -> bool -> must_not -> exist
- Greater Than >:
    - Code: BoolQuery.Builder.must(RangeQuery with operator: gt)
    - Query: query -> bool -> must -> range
- Greater Than Equal >=:
    - Code: BoolQuery.Builder.must(RangeQuery with operator: gte)
    - Query: query -> bool -> must -> range
- Less Than <:
    - Code: BoolQuery.Builder.must(RangeQuery with operator: lt)
    - Query: query -> bool -> must -> range
- Less Than Equal <=:
    - Code: BoolQuery.Builder.must(RangeQuery with operator: lte)
    - Query: query -> bool -> must -> range
- Synonym:
    - Code: BoolQuery.Builder.must(Query.matchPhrase(MatchPhraseQuery))
    - Query: query -> bool -> must -> match_phrase(with analyzer that added in OpenSearch's synonum)
- Should:
    - minimumShouldMatch: 1 (need 1 condition match)
- Paging:
    - Offset/limit: SearchRequest.Builder.offset/limit
    - base on value of specific field: aggregation + bucket + bucketSort(offset, limit)

Mapping:
- BoolQuery <=> bool
- Query <=> query

Operator:
- BoolQuery.Builder.must <=> must = AND
- BoolQuery.Builder.should <=> should = OR

Function Score:
- Used to custom matching score calculation
- Code: FunctionScore.scriptScore(ScriptScoreFunction.script.inline(lang, souce(your custom filter score)))
- Each query can include multiple function_score functions and combine them using modes like max, min, or sum to compute the final score. However, a query should ideally use only one function_score when the final score depends on a specific condition. For example, if one function returns an invalid score (e.g., 0), the entire document should be considered invalid, which cannot be enforced using the current scoring modes.

Example query with custom score calculation:
```
{
  "from": 0,
  "min_score": 0.00001,
  "query": {
    "function_score": {
      "boost_mode": "replace",
      "functions": [  //<Where to apply filters after retrieving results from queryCondition>
        {
          "script_score": {
            "script": {
              "lang": "painless",
              "source": "<score script>"
            }
          }
        }
      ],
      "query": {  //<queryCondition: Defines where query conditions are specified>
        "bool": {
          "must": [ ],
          "must_not": [ ]
        }
      },
      "score_mode": "sum"
    }
  },
  "size": 100
}
```

- Sample queryCondition
```
query
  bool
    must
      range (1)
      exists (2)
      match_phrase (3)
      term (4)
      should
        range (5)
        term (6)
    must_not
      term (7)
      exists (8)
```
=> condition (1 and 2 and 3 and 4 and (5 or 6)) and (7 and 8)

 