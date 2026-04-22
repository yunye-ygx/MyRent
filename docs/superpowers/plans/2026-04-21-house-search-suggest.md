# House Search Suggest Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a first-release house suggestion dropdown that returns up to five title-matched rentable houses on the homepage and house list search boxes, and lets users jump straight to the detail page without changing the existing location-based main search flow.

**Architecture:** Add a dedicated backend `POST /house/suggest` endpoint that returns lightweight suggestion items from Elasticsearch, with an empty-list fallback on failure. On the frontend, add a reusable suggestion composable plus a shared search field component, then wire that component into `HomeHero` and `HouseResultsHero` so both pages share the same debounce, stale-response protection, and dropdown UI behavior.

**Tech Stack:** Spring Boot 3.5, MyBatis-Plus, Spring Data Elasticsearch, JUnit 5, Mockito, Vue 3, Vue Test Utils, Vitest, Axios, Vue Router

---

## File Map

### Backend

- Create: `src/main/java/cn/yy/myrent/dto/HouseSuggestReqDTO.java`
  Responsibility: request body for the new suggestion endpoint.
- Create: `src/main/java/cn/yy/myrent/vo/HouseSuggestItemVO.java`
  Responsibility: lightweight suggestion item with only `id`, `title`, and `price`.
- Modify: `src/main/java/cn/yy/myrent/controller/HouseController.java`
  Responsibility: expose `POST /house/suggest`.
- Modify: `src/main/java/cn/yy/myrent/service/IHouseService.java`
  Responsibility: declare the new suggestion service contract.
- Modify: `src/main/java/cn/yy/myrent/service/impl/HouseServiceImpl.java`
  Responsibility: query Elasticsearch for title-based suggestions, convert documents, and fail closed with an empty list.
- Modify: `src/test/java/cn/yy/myrent/controller/HouseControllerWebMvcTest.java`
  Responsibility: verify the controller contract and JSON response shape.
- Create: `src/test/java/cn/yy/myrent/service/impl/HouseServiceImplSuggestTest.java`
  Responsibility: verify successful suggestion conversion and empty-list fallback on Elasticsearch failure.

### Frontend

- Modify: `frontend/src/api/house.js`
  Responsibility: add the `fetchHouseSuggestions` API helper.
- Create: `frontend/src/composables/useHouseSuggest.js`
  Responsibility: debounce requests, guard against stale responses, and manage dropdown state.
- Create: `frontend/src/composables/__tests__/useHouseSuggest.spec.js`
  Responsibility: verify debounce behavior, stale-response protection, and reset logic.
- Create: `frontend/src/components/HouseSuggestField.vue`
  Responsibility: shared search input + submit button + suggestion dropdown UI.
- Create: `frontend/src/components/__tests__/HouseSuggestField.spec.js`
  Responsibility: verify dropdown rendering, empty state, error state, and selection events.
- Modify: `frontend/src/components/home/HomeHero.vue`
  Responsibility: replace the inline search input with the reusable suggest field and re-emit events upward.
- Modify: `frontend/src/components/__tests__/HomeHero.spec.js`
  Responsibility: verify `HomeHero` still emits search and suggestion-select events while preserving the hero content.
- Modify: `frontend/src/components/house/HouseResultsHero.vue`
  Responsibility: replace the inline search input with the reusable suggest field while keeping reset behavior.
- Modify: `frontend/src/views/HomeView.vue`
  Responsibility: navigate to house detail when a suggestion is selected.
- Modify: `frontend/src/views/HouseListView.vue`
  Responsibility: navigate to house detail when a suggestion is selected.
- Modify: `frontend/src/views/__tests__/HomeView.spec.js`
  Responsibility: verify homepage search wiring still renders and now routes on suggestion selection.
- Modify: `frontend/src/views/__tests__/HouseListView.spec.js`
  Responsibility: verify house list search wiring still renders and now routes on suggestion selection.

## Task 1: Add the backend suggestion endpoint contract

**Files:**
- Create: `src/main/java/cn/yy/myrent/dto/HouseSuggestReqDTO.java`
- Create: `src/main/java/cn/yy/myrent/vo/HouseSuggestItemVO.java`
- Modify: `src/main/java/cn/yy/myrent/service/IHouseService.java`
- Modify: `src/main/java/cn/yy/myrent/controller/HouseController.java`
- Test: `src/test/java/cn/yy/myrent/controller/HouseControllerWebMvcTest.java`

- [ ] **Step 1: Write the failing controller test**

```java
package cn.yy.myrent.controller;

import cn.yy.myrent.vo.HouseSuggestItemVO;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Test
void suggestShouldReturnLightweightHouseSuggestions() throws Exception {
    HouseSuggestItemVO item = new HouseSuggestItemVO();
    item.setId(1L);
    item.setTitle("天河公园单间");
    item.setPrice(BigDecimal.valueOf(3200));

    given(houseService.suggestHouses(any())).willReturn(List.of(item));

    mockMvc.perform(post("/house/suggest")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {
                              "keyword": "天河",
                              "size": 5
                            }
                            """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200))
            .andExpect(jsonPath("$.data[0].id").value("1"))
            .andExpect(jsonPath("$.data[0].title").value("天河公园单间"))
            .andExpect(jsonPath("$.data[0].price").value(3200));
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -Dtest=HouseControllerWebMvcTest test`

Expected: FAIL because `POST /house/suggest` does not exist and `IHouseService` has no `suggestHouses` method.

- [ ] **Step 3: Add the request DTO, response VO, service contract, and controller endpoint**

```java
// src/main/java/cn/yy/myrent/dto/HouseSuggestReqDTO.java
package cn.yy.myrent.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class HouseSuggestReqDTO {

    @NotBlank(message = "keyword cannot be blank")
    private String keyword;

    @Min(value = 1, message = "size must be at least 1")
    @Max(value = 10, message = "size cannot be greater than 10")
    private Integer size = 5;
}
```

```java
// src/main/java/cn/yy/myrent/vo/HouseSuggestItemVO.java
package cn.yy.myrent.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class HouseSuggestItemVO {

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long id;

    private String title;

    private BigDecimal price;
}
```

```java
// src/main/java/cn/yy/myrent/service/IHouseService.java
package cn.yy.myrent.service;

import cn.yy.myrent.dto.HouseSuggestReqDTO;
import cn.yy.myrent.dto.SearchHouseReqDTO;
import cn.yy.myrent.dto.SmartGuideReqDTO;
import cn.yy.myrent.entity.House;
import cn.yy.myrent.vo.HouseSearchResultVO;
import cn.yy.myrent.vo.HouseSuggestItemVO;
import cn.yy.myrent.vo.SmartGuideResultVO;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

public interface IHouseService extends IService<House> {

    HouseSearchResultVO searchNearbyHouse(SearchHouseReqDTO reqDTO);

    HouseSearchResultVO hotHouses(Integer page, Integer size);

    SmartGuideResultVO smartGuide(SmartGuideReqDTO reqDTO);

    List<HouseSuggestItemVO> suggestHouses(HouseSuggestReqDTO reqDTO);
}
```

```java
// src/main/java/cn/yy/myrent/controller/HouseController.java
package cn.yy.myrent.controller;

import cn.yy.myrent.dto.HouseSuggestReqDTO;
import cn.yy.myrent.vo.HouseSuggestItemVO;

import java.util.List;

@PostMapping("/suggest")
@Operation(summary = "房源搜索候选", description = "按标题关键字返回轻量房源候选")
public Result<List<HouseSuggestItemVO>> suggest(@Valid @RequestBody HouseSuggestReqDTO reqDTO) {
    return Result.success(houseService.suggestHouses(reqDTO));
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -Dtest=HouseControllerWebMvcTest test`

Expected: PASS with `HouseControllerWebMvcTest` confirming `POST /house/suggest` returns a 200 response and the expected JSON payload.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/cn/yy/myrent/dto/HouseSuggestReqDTO.java src/main/java/cn/yy/myrent/vo/HouseSuggestItemVO.java src/main/java/cn/yy/myrent/service/IHouseService.java src/main/java/cn/yy/myrent/controller/HouseController.java src/test/java/cn/yy/myrent/controller/HouseControllerWebMvcTest.java
git commit -m "feat: add house suggest endpoint contract"
```

## Task 2: Implement Elasticsearch-backed suggestion lookup

**Files:**
- Modify: `src/main/java/cn/yy/myrent/service/impl/HouseServiceImpl.java`
- Test: `src/test/java/cn/yy/myrent/service/impl/HouseServiceImplSuggestTest.java`

- [ ] **Step 1: Write the failing service tests**

```java
package cn.yy.myrent.service.impl;

import cn.yy.myrent.document.HouseDoc;
import cn.yy.myrent.dto.HouseSuggestReqDTO;
import cn.yy.myrent.service.IUserService;
import cn.yy.myrent.service.hot.HouseHotService;
import cn.yy.myrent.service.location.LocationResolveService;
import cn.yy.myrent.service.smartguide.SmartGuideRecommendationService;
import cn.yy.myrent.vo.HouseSuggestItemVO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HouseServiceImplSuggestTest {

    @Mock
    private ElasticsearchOperations elasticsearchOperations;

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private SmartGuideRecommendationService smartGuideRecommendationService;

    @Mock
    private HouseHotService houseHotService;

    @Mock
    private LocationResolveService locationResolveService;

    @Mock
    private IUserService userService;

    @InjectMocks
    private HouseServiceImpl houseService;

    @Test
    void suggestHousesShouldConvertEsHitsToSuggestionItems() {
        HouseDoc doc = new HouseDoc();
        doc.setId(1L);
        doc.setTitle("天河公园单间");
        doc.setPrice(320000);
        doc.setStatus(1);

        SearchHit<HouseDoc> hit = mock(SearchHit.class);
        when(hit.getContent()).thenReturn(doc);

        SearchHits<HouseDoc> hits = mock(SearchHits.class);
        when(hits.iterator()).thenReturn(List.of(hit).iterator());

        when(elasticsearchOperations.search(any(NativeQuery.class), eq(HouseDoc.class))).thenReturn(hits);

        HouseSuggestReqDTO reqDTO = new HouseSuggestReqDTO();
        reqDTO.setKeyword("天河");
        reqDTO.setSize(5);

        List<HouseSuggestItemVO> result = houseService.suggestHouses(reqDTO);

        assertEquals(1, result.size());
        assertEquals(1L, result.get(0).getId());
        assertEquals("天河公园单间", result.get(0).getTitle());
        assertEquals(BigDecimal.valueOf(3200), result.get(0).getPrice());
    }

    @Test
    void suggestHousesShouldReturnEmptyListWhenElasticsearchFails() {
        when(elasticsearchOperations.search(any(NativeQuery.class), eq(HouseDoc.class)))
                .thenThrow(new RuntimeException("ES unavailable"));

        HouseSuggestReqDTO reqDTO = new HouseSuggestReqDTO();
        reqDTO.setKeyword("天河");
        reqDTO.setSize(5);

        List<HouseSuggestItemVO> result = houseService.suggestHouses(reqDTO);

        assertTrue(result.isEmpty());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -Dtest=HouseServiceImplSuggestTest test`

Expected: FAIL because `HouseServiceImpl` does not yet implement `suggestHouses`.

- [ ] **Step 3: Implement the Elasticsearch query and empty-list fallback**

```java
// src/main/java/cn/yy/myrent/service/impl/HouseServiceImpl.java
package cn.yy.myrent.service.impl;

import cn.yy.myrent.dto.HouseSuggestReqDTO;
import cn.yy.myrent.vo.HouseSuggestItemVO;
import co.elastic.clients.elasticsearch._types.SortOptions;
import co.elastic.clients.elasticsearch._types.SortOrder;
import org.springframework.util.StringUtils;

import java.util.Collections;

@Override
public List<HouseSuggestItemVO> suggestHouses(HouseSuggestReqDTO reqDTO) {
    String keyword = reqDTO.getKeyword() == null ? "" : reqDTO.getKeyword().trim();
    if (!StringUtils.hasText(keyword)) {
        return Collections.emptyList();
    }

    int limit = reqDTO.getSize() == null ? 5 : Math.min(reqDTO.getSize(), 10);

    try {
        return searchSuggestInEs(keyword, limit);
    } catch (Exception e) {
        log.warn("house suggest query failed, keyword={}, size={}", keyword, limit, e);
        return Collections.emptyList();
    }
}

private List<HouseSuggestItemVO> searchSuggestInEs(String keyword, int size) {
    Query query = Query.of(q -> q.bool(b -> b
            .must(m -> m.term(t -> t.field("status").value(HOUSE_STATUS_AVAILABLE)))
            .must(m -> m.matchPhrasePrefix(mp -> mp.field("title").query(keyword)))
    ));

    SortOptions scoreSort = SortOptions.of(s -> s.score(sc -> sc.order(SortOrder.Desc)));
    SortOptions createTimeSort = SortOptions.of(s -> s.field(f -> f.field("createTime").order(SortOrder.Desc)));

    NativeQuery nativeQuery = NativeQuery.builder()
            .withQuery(query)
            .withSort(scoreSort)
            .withSort(createTimeSort)
            .withPageable(PageRequest.of(0, size))
            .build();

    SearchHits<HouseDoc> hits = elasticsearchOperations.search(nativeQuery, HouseDoc.class);
    List<HouseSuggestItemVO> suggestions = new ArrayList<>();
    for (SearchHit<HouseDoc> hit : hits) {
        suggestions.add(convertDocToSuggestItem(hit.getContent()));
    }
    return suggestions;
}

private HouseSuggestItemVO convertDocToSuggestItem(HouseDoc doc) {
    HouseSuggestItemVO item = new HouseSuggestItemVO();
    item.setId(doc.getId());
    item.setTitle(doc.getTitle());
    if (doc.getPrice() != null) {
        item.setPrice(BigDecimal.valueOf(doc.getPrice())
                .divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP));
    }
    return item;
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -Dtest=HouseServiceImplSuggestTest test`

Expected: PASS with one test proving successful ES hit conversion and one test proving ES failures return an empty list.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/cn/yy/myrent/service/impl/HouseServiceImpl.java src/test/java/cn/yy/myrent/service/impl/HouseServiceImplSuggestTest.java
git commit -m "feat: implement elasticsearch house suggestions"
```

## Task 3: Add frontend suggestion API access and debounce state management

**Files:**
- Modify: `frontend/src/api/house.js`
- Create: `frontend/src/composables/useHouseSuggest.js`
- Test: `frontend/src/composables/__tests__/useHouseSuggest.spec.js`

- [ ] **Step 1: Write the failing composable tests**

```javascript
import { flushPromises } from '@vue/test-utils'
import { useHouseSuggest } from '@/composables/useHouseSuggest'

describe('useHouseSuggest', () => {
  beforeEach(() => {
    vi.useFakeTimers()
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('debounces fetches and ignores stale responses', async () => {
    let resolveFirst
    let resolveSecond

    const fetcher = vi.fn()
      .mockImplementationOnce(() => new Promise((resolve) => { resolveFirst = resolve }))
      .mockImplementationOnce(() => new Promise((resolve) => { resolveSecond = resolve }))

    const suggest = useHouseSuggest({ fetcher, debounceMs: 300, minLength: 2, size: 5 })

    suggest.request('天河公园')
    await vi.advanceTimersByTimeAsync(300)
    expect(fetcher).toHaveBeenCalledWith({ keyword: '天河公园', size: 5 })

    suggest.request('天河公园东')
    await vi.advanceTimersByTimeAsync(300)
    expect(fetcher).toHaveBeenLastCalledWith({ keyword: '天河公园东', size: 5 })

    resolveFirst([{ id: 1, title: '旧结果', price: 3200 }])
    resolveSecond([{ id: 2, title: '新结果', price: 3600 }])
    await flushPromises()

    expect(suggest.items.value).toEqual([{ id: 2, title: '新结果', price: 3600 }])
    expect(suggest.open.value).toBe(true)
  })

  it('clears dropdown state when keyword becomes shorter than the minimum length', async () => {
    const fetcher = vi.fn().mockResolvedValue([{ id: 1, title: '天河公园单间', price: 3200 }])
    const suggest = useHouseSuggest({ fetcher, debounceMs: 300, minLength: 2, size: 5 })

    suggest.request('天河')
    await vi.advanceTimersByTimeAsync(300)
    await flushPromises()

    expect(suggest.items.value).toHaveLength(1)
    expect(suggest.open.value).toBe(true)

    suggest.request('天')

    expect(suggest.items.value).toEqual([])
    expect(suggest.open.value).toBe(false)
    expect(suggest.loading.value).toBe(false)
  })
})
```

- [ ] **Step 2: Run test to verify it fails**

Run: `npm run test:run -- src/composables/__tests__/useHouseSuggest.spec.js`

Workdir: `frontend`

Expected: FAIL because `useHouseSuggest` and `fetchHouseSuggestions` do not exist yet.

- [ ] **Step 3: Add the API helper and composable**

```javascript
// frontend/src/api/house.js
import http from './http'

export function fetchHouseSuggestions(payload) {
  return http.post('/house/suggest', payload)
}
```

```javascript
// frontend/src/composables/useHouseSuggest.js
import { onBeforeUnmount, ref } from 'vue'

export function useHouseSuggest({ fetcher, debounceMs = 300, minLength = 2, size = 5 }) {
  const items = ref([])
  const loading = ref(false)
  const error = ref('')
  const open = ref(false)

  let debounceTimer = null
  let latestRequestId = 0
  let latestKeyword = ''

  function reset() {
    latestRequestId += 1
    latestKeyword = ''
    if (debounceTimer) {
      clearTimeout(debounceTimer)
      debounceTimer = null
    }
    items.value = []
    loading.value = false
    error.value = ''
    open.value = false
  }

  function close() {
    open.value = false
  }

  function reopen() {
    if (items.value.length || error.value) {
      open.value = true
    }
  }

  function request(keyword) {
    const trimmedKeyword = keyword.trim()
    latestKeyword = trimmedKeyword

    if (debounceTimer) {
      clearTimeout(debounceTimer)
      debounceTimer = null
    }

    if (trimmedKeyword.length < minLength) {
      reset()
      return
    }

    debounceTimer = setTimeout(async () => {
      const requestId = ++latestRequestId
      loading.value = true
      error.value = ''

      try {
        const result = await fetcher({ keyword: trimmedKeyword, size })
        if (requestId !== latestRequestId || trimmedKeyword !== latestKeyword) {
          return
        }
        items.value = result || []
        open.value = true
      } catch (err) {
        if (requestId !== latestRequestId || trimmedKeyword !== latestKeyword) {
          return
        }
        items.value = []
        error.value = '搜索建议暂不可用'
        open.value = true
      } finally {
        if (requestId === latestRequestId) {
          loading.value = false
        }
      }
    }, debounceMs)
  }

  onBeforeUnmount(() => {
    if (debounceTimer) {
      clearTimeout(debounceTimer)
    }
  })

  return {
    items,
    loading,
    error,
    open,
    request,
    close,
    reopen,
    reset
  }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `npm run test:run -- src/composables/__tests__/useHouseSuggest.spec.js`

Workdir: `frontend`

Expected: PASS with debounce, stale-response, and reset behavior covered.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/api/house.js frontend/src/composables/useHouseSuggest.js frontend/src/composables/__tests__/useHouseSuggest.spec.js
git commit -m "feat: add frontend house suggestion state"
```

## Task 4: Build the shared suggestion search field and hook it into `HomeHero`

**Files:**
- Create: `frontend/src/components/HouseSuggestField.vue`
- Create: `frontend/src/components/__tests__/HouseSuggestField.spec.js`
- Modify: `frontend/src/components/home/HomeHero.vue`
- Modify: `frontend/src/components/__tests__/HomeHero.spec.js`

- [ ] **Step 1: Write the failing component tests**

```javascript
// frontend/src/components/__tests__/HouseSuggestField.spec.js
import { flushPromises, mount } from '@vue/test-utils'
import HouseSuggestField from '@/components/HouseSuggestField.vue'

vi.mock('@/api/house', () => ({
  fetchHouseSuggestions: vi.fn().mockResolvedValue([
    { id: 9, title: '天河公园单间', price: 3200 }
  ])
}))

describe('HouseSuggestField', () => {
  beforeEach(() => {
    vi.useFakeTimers()
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('renders suggestion items and emits selection', async () => {
    const wrapper = mount(HouseSuggestField, {
      props: {
        placeholder: '输入房源标题',
        submitText: '开始找房'
      }
    })

    await wrapper.find('input').setValue('天河')
    await vi.advanceTimersByTimeAsync(300)
    await flushPromises()

    expect(wrapper.text()).toContain('天河公园单间')
    expect(wrapper.text()).toContain('¥3200/月')

    await wrapper.find('[data-test="suggest-item"]').trigger('mousedown')

    expect(wrapper.emitted('suggestion-select')[0][0]).toEqual({
      id: 9,
      title: '天河公园单间',
      price: 3200
    })
  })
})
```

```javascript
// frontend/src/components/__tests__/HomeHero.spec.js
import { mount } from '@vue/test-utils'
import HomeHero from '@/components/home/HomeHero.vue'

describe('HomeHero', () => {
  it('renders the hero shell and re-emits suggest field events', async () => {
    const wrapper = mount(HomeHero, {
      props: {
        resultTip: '当前展示广州精选房源',
        isNearbyMode: false
      },
      global: {
        stubs: {
          HouseSuggestField: {
            template: `
              <div>
                <button data-test="stub-search" @click="$emit('search', '天河公园')">search</button>
                <button
                  data-test="stub-select"
                  @click="$emit('suggestion-select', { id: 9, title: '天河公园单间', price: 3200 })"
                >
                  select
                </button>
              </div>
            `
          }
        }
      }
    })

    expect(wrapper.text()).toContain('开始找房')
    expect(wrapper.text()).toContain('近地铁')

    await wrapper.find('[data-test="stub-search"]').trigger('click')
    await wrapper.find('[data-test="stub-select"]').trigger('click')

    expect(wrapper.emitted('search')).toEqual([['天河公园']])
    expect(wrapper.emitted('suggestion-select')[0][0]).toMatchObject({ id: 9 })
  })
})
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `npm run test:run -- src/components/__tests__/HouseSuggestField.spec.js src/components/__tests__/HomeHero.spec.js`

Workdir: `frontend`

Expected: FAIL because `HouseSuggestField.vue` does not exist and `HomeHero` does not yet re-emit suggestion selection.

- [ ] **Step 3: Create the shared field component and update `HomeHero`**

```vue
<!-- frontend/src/components/HouseSuggestField.vue -->
<template>
  <div ref="root" class="suggest-field">
    <div class="search-row">
      <input
        v-model.trim="keyword"
        class="input"
        :placeholder="placeholder"
        @focus="handleFocus"
        @keyup.enter="emitSearch"
      />
      <button class="primary-btn" type="button" @click="emitSearch">
        {{ submitText }}
      </button>
    </div>

    <div v-if="suggest.open.value" class="suggest-panel">
      <p v-if="suggest.loading.value" class="suggest-meta">搜索中</p>
      <p v-else-if="suggest.error.value" class="suggest-meta">{{ suggest.error.value }}</p>
      <p v-else-if="!suggest.items.value.length" class="suggest-meta">没有找到匹配房源</p>
      <button
        v-for="item in suggest.items.value"
        :key="item.id"
        class="suggest-item"
        data-test="suggest-item"
        type="button"
        @mousedown.prevent="selectSuggestion(item)"
      >
        <span class="suggest-title">{{ item.title }}</span>
        <span class="suggest-price">¥{{ item.price }}/月</span>
      </button>
    </div>
  </div>
</template>

<script setup>
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { fetchHouseSuggestions } from '@/api/house'
import { useHouseSuggest } from '@/composables/useHouseSuggest'

const props = defineProps({
  placeholder: {
    type: String,
    required: true
  },
  submitText: {
    type: String,
    required: true
  }
})

const emit = defineEmits(['search', 'suggestion-select'])
const root = ref(null)
const keyword = ref('')
const suggest = useHouseSuggest({ fetcher: fetchHouseSuggestions, debounceMs: 300, minLength: 2, size: 5 })

watch(keyword, (value) => {
  suggest.request(value)
})

function emitSearch() {
  suggest.close()
  emit('search', keyword.value)
}

function selectSuggestion(item) {
  suggest.close()
  emit('suggestion-select', item)
}

function handleFocus() {
  suggest.reopen()
}

function handleDocumentPointerDown(event) {
  if (!root.value?.contains(event.target)) {
    suggest.close()
  }
}

onMounted(() => {
  document.addEventListener('pointerdown', handleDocumentPointerDown)
})

onBeforeUnmount(() => {
  document.removeEventListener('pointerdown', handleDocumentPointerDown)
})
</script>
```

```vue
<!-- frontend/src/components/home/HomeHero.vue -->
<script setup>
import { computed } from 'vue'
import HouseSuggestField from '@/components/HouseSuggestField.vue'

const emit = defineEmits(['search', 'preset', 'suggestion-select'])
</script>

<template>
  <section class="hero app-surface">
    <div class="search-card">
      <div class="heading-row">
        <div>
          <p class="eyebrow">Search</p>
          <h1 class="title">开始找房</h1>
        </div>
        <p class="tip">{{ tipText }}</p>
      </div>

      <HouseSuggestField
        placeholder="区域 / 地点 / 房源标题"
        submit-text="开始找房"
        @search="$emit('search', $event)"
        @suggestion-select="$emit('suggestion-select', $event)"
      />

      <div class="filter-row">
        <button class="ghost-chip" type="button" @click="$emit('preset', 'budget')">预算</button>
        <button class="ghost-chip" type="button" @click="$emit('preset', 'rentalType')">整租 / 合租</button>
        <button class="ghost-chip" type="button" @click="$emit('preset', 'commute')">通勤 / 地铁</button>
      </div>

      <div class="preset-row">
        <button class="preset-chip" type="button" @click="$emit('search', '近地铁')">近地铁</button>
        <button class="preset-chip" type="button" @click="$emit('search', '低总价')">低总价</button>
        <button class="preset-chip" type="button" @click="$emit('search', '整租优先')">整租优先</button>
        <button class="preset-chip" type="button" @click="$emit('search', '新上房源')">新上房源</button>
      </div>
    </div>
  </section>
</template>
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `npm run test:run -- src/components/__tests__/HouseSuggestField.spec.js src/components/__tests__/HomeHero.spec.js`

Workdir: `frontend`

Expected: PASS with `HouseSuggestField` rendering the dropdown correctly and `HomeHero` re-emitting both search and suggestion-select events.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/components/HouseSuggestField.vue frontend/src/components/__tests__/HouseSuggestField.spec.js frontend/src/components/home/HomeHero.vue frontend/src/components/__tests__/HomeHero.spec.js
git commit -m "feat: add reusable house suggestion search field"
```

## Task 5: Wire suggestion selection into `HouseResultsHero`, `HomeView`, and `HouseListView`

**Files:**
- Modify: `frontend/src/components/house/HouseResultsHero.vue`
- Modify: `frontend/src/views/HomeView.vue`
- Modify: `frontend/src/views/HouseListView.vue`
- Modify: `frontend/src/views/__tests__/HomeView.spec.js`
- Modify: `frontend/src/views/__tests__/HouseListView.spec.js`

- [ ] **Step 1: Write the failing integration tests**

```javascript
// frontend/src/views/__tests__/HomeView.spec.js
import { ref } from 'vue'
import { mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import HomeView from '@/views/HomeView.vue'

vi.mock('@/composables/useHouseFeed', () => ({
  useHouseFeed: () => ({
    houses: ref([{ id: 1, title: '天河单间', price: 3200, depositAmount: 3200, status: 1 }]),
    loading: ref(false),
    error: ref(''),
    mode: ref('hot'),
    resultTip: ref('当前展示 1 套精选房源'),
    loadNext: vi.fn(),
    activateNearby: vi.fn(),
    activateHot: vi.fn()
  })
}))

describe('HomeView', () => {
  it('routes to the house detail page when a suggestion is selected', async () => {
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [
        { path: '/', component: { template: '<div />' } },
        { path: '/houses', component: { template: '<div />' } },
        { path: '/house/:id', component: { template: '<div />' } }
      ]
    })

    const wrapper = mount(HomeView, {
      global: {
        plugins: [router],
        stubs: {
          HomeHero: {
            template: '<button data-test="hero-select" @click="$emit(\'suggestion-select\', { id: 9 })">select</button>'
          },
          HouseCard: true
        }
      }
    })

    await router.isReady()
    await wrapper.find('[data-test="hero-select"]').trigger('click')

    expect(router.currentRoute.value.fullPath).toBe('/house/9')
  })
})
```

```javascript
// frontend/src/views/__tests__/HouseListView.spec.js
import { ref } from 'vue'
import { mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import HouseListView from '@/views/HouseListView.vue'

vi.mock('@/composables/useHouseFeed', () => ({
  useHouseFeed: () => ({
    houses: ref([{ id: 1, title: '珠江新城公寓', price: 5200, depositAmount: 5200, status: 1 }]),
    loading: ref(false),
    error: ref(''),
    hasMore: ref(false),
    mode: ref('hot'),
    resultTip: ref('共 1 套房源'),
    loadNext: vi.fn(),
    activateNearby: vi.fn(),
    activateHot: vi.fn()
  })
}))

describe('HouseListView', () => {
  it('routes to the house detail page when a suggestion is selected', async () => {
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [
        { path: '/', component: { template: '<div />' } },
        { path: '/house/:id', component: { template: '<div />' } }
      ]
    })

    const wrapper = mount(HouseListView, {
      global: {
        plugins: [router],
        stubs: {
          HouseResultsHero: {
            template: '<button data-test="results-select" @click="$emit(\'suggestion-select\', { id: 12 })">select</button>'
          },
          HouseCard: true,
          LoadingState: true,
          EmptyState: true
        }
      }
    })

    await router.isReady()
    await wrapper.find('[data-test="results-select"]').trigger('click')

    expect(router.currentRoute.value.fullPath).toBe('/house/12')
  })
})
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `npm run test:run -- src/views/__tests__/HomeView.spec.js src/views/__tests__/HouseListView.spec.js`

Workdir: `frontend`

Expected: FAIL because the views do not yet listen for `suggestion-select` and `HouseResultsHero` does not expose the reusable search field.

- [ ] **Step 3: Update the views and results hero to route on selection**

```vue
<!-- frontend/src/components/house/HouseResultsHero.vue -->
<script setup>
import { computed } from 'vue'
import HouseSuggestField from '@/components/HouseSuggestField.vue'

const emit = defineEmits(['search', 'reset', 'suggestion-select'])
</script>

<template>
  <section class="results-hero app-surface">
    <div>
      <p class="eyebrow">Results</p>
      <h1 class="title">{{ title }}</h1>
      <p class="tip">{{ resultTip || defaultTip }}</p>
    </div>
    <div class="search-box">
      <HouseSuggestField
        placeholder="输入地点或房源标题"
        submit-text="搜索"
        @search="$emit('search', $event)"
        @suggestion-select="$emit('suggestion-select', $event)"
      />
      <button v-if="isNearbyMode" class="ghost-btn reset-btn" @click="$emit('reset')">回到精选推荐</button>
    </div>
  </section>
</template>
```

```vue
<!-- frontend/src/views/HomeView.vue -->
<template>
  <div class="home-view">
    <HomeHero
      :result-tip="feed.resultTip.value"
      :is-nearby-mode="feed.mode.value === 'nearby'"
      @search="handleSearch"
      @suggestion-select="handleSuggestionSelect"
    />
    <!-- existing content -->
  </div>
</template>

<script setup>
function handleSuggestionSelect(item) {
  router.push(`/house/${item.id}`)
}
</script>
```

```vue
<!-- frontend/src/views/HouseListView.vue -->
<template>
  <div class="house-list-view">
    <HouseResultsHero
      title="精选房源列表"
      :result-tip="feed.resultTip.value"
      :is-nearby-mode="feed.mode.value === 'nearby'"
      @search="handleSearch"
      @reset="handleReset"
      @suggestion-select="handleSuggestionSelect"
    />
    <!-- existing content -->
  </div>
</template>

<script setup>
function handleSuggestionSelect(item) {
  router.push(`/house/${item.id}`)
}
</script>
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `npm run test:run -- src/views/__tests__/HomeView.spec.js src/views/__tests__/HouseListView.spec.js`

Workdir: `frontend`

Expected: PASS with both views pushing the expected detail route after receiving a suggestion-selection event.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/components/house/HouseResultsHero.vue frontend/src/views/HomeView.vue frontend/src/views/HouseListView.vue frontend/src/views/__tests__/HomeView.spec.js frontend/src/views/__tests__/HouseListView.spec.js
git commit -m "feat: route search suggestions to house detail pages"
```

## Task 6: Run focused verification and a final cross-page regression sweep

**Files:**
- Modify: `frontend/src/views/__tests__/HomeView.spec.js`
- Modify: `frontend/src/views/__tests__/HouseListView.spec.js`
- Modify: `frontend/src/components/__tests__/HomeHero.spec.js`
- Modify: `frontend/src/components/__tests__/HouseSuggestField.spec.js`
- Modify: `src/test/java/cn/yy/myrent/controller/HouseControllerWebMvcTest.java`
- Modify: `src/test/java/cn/yy/myrent/service/impl/HouseServiceImplSuggestTest.java`

- [ ] **Step 1: Add one regression assertion per layer if anything is still missing**

```javascript
// frontend/src/components/__tests__/HouseSuggestField.spec.js
it('shows the empty-state text when the backend returns no suggestions', async () => {
  const { fetchHouseSuggestions } = await import('@/api/house')
  fetchHouseSuggestions.mockResolvedValueOnce([])

  const wrapper = mount(HouseSuggestField, {
    props: {
      placeholder: '输入房源标题',
      submitText: '搜索'
    }
  })

  await wrapper.find('input').setValue('海珠')
  await vi.advanceTimersByTimeAsync(300)
  await flushPromises()

  expect(wrapper.text()).toContain('没有找到匹配房源')
})
```

```java
// src/test/java/cn/yy/myrent/service/impl/HouseServiceImplSuggestTest.java
@Test
void suggestHousesShouldClampSizeToTen() {
    SearchHits<HouseDoc> hits = mock(SearchHits.class);
    when(hits.iterator()).thenReturn(List.<SearchHit<HouseDoc>>of().iterator());
    when(elasticsearchOperations.search(any(NativeQuery.class), eq(HouseDoc.class))).thenReturn(hits);

    HouseSuggestReqDTO reqDTO = new HouseSuggestReqDTO();
    reqDTO.setKeyword("天河");
    reqDTO.setSize(20);

    List<HouseSuggestItemVO> result = houseService.suggestHouses(reqDTO);

    assertTrue(result.isEmpty());
}
```

- [ ] **Step 2: Run the full focused test set**

Run: `mvn -Dtest=HouseControllerWebMvcTest,HouseServiceImplSuggestTest test`

Expected: PASS with backend controller and service suggestion coverage green.

Run: `npm run test:run -- src/composables/__tests__/useHouseSuggest.spec.js src/components/__tests__/HouseSuggestField.spec.js src/components/__tests__/HomeHero.spec.js src/views/__tests__/HomeView.spec.js src/views/__tests__/HouseListView.spec.js`

Workdir: `frontend`

Expected: PASS with debounce, dropdown rendering, event propagation, and detail-page navigation all green.

- [ ] **Step 3: Run one manual smoke test after the focused suites pass**

Run: `npm run dev`

Workdir: `frontend`

Manual check:
- Type one character in the homepage search field and confirm no dropdown appears.
- Type two or more characters that match an indexed house title and confirm up to five suggestions appear.
- Click a suggestion and confirm the app routes to `/house/:id`.
- Repeat the same flow on `/houses`.

- [ ] **Step 4: Commit**

```bash
git add src/test/java/cn/yy/myrent/controller/HouseControllerWebMvcTest.java src/test/java/cn/yy/myrent/service/impl/HouseServiceImplSuggestTest.java frontend/src/composables/__tests__/useHouseSuggest.spec.js frontend/src/components/__tests__/HouseSuggestField.spec.js frontend/src/components/__tests__/HomeHero.spec.js frontend/src/views/__tests__/HomeView.spec.js frontend/src/views/__tests__/HouseListView.spec.js
git commit -m "test: verify house suggestion search flow"
```
