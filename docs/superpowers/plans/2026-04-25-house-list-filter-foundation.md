# House List Filter Foundation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the first stable house-list filtering foundation by separating list filters from smart recommendation, adding `city` and `region` to house data, and making the frontend region options depend on the selected hot city.

**Architecture:** Keep the nine hot cities and their region lists as frontend static configuration because they are demo dictionary data. Persist `city` and `region` on each `house` record so the backend can filter deterministically by structured fields; do not reuse `smart-guide` for this workflow. Add a dedicated list filter request path that supports `city`, `region`, `price`, `rentType`, pagination, and returns ordinary list results.

**Tech Stack:** Spring Boot 3.5, MyBatis-Plus, MySQL 8, Spring Data Elasticsearch, Vue 3, Pinia, Axios, Vue Test Utils, Vitest, JUnit 5, Mockito

---

## File Map

### Frontend

- Create: `frontend/src/config/cityFilters.js`
  Responsibility: define the nine hot cities, each city's region list, and shared helper functions such as default city lookup.
- Modify: `frontend/src/stores/auth.js`
  Responsibility: persist the currently selected city in profile state and expose an action to switch city.
- Modify: `frontend/src/components/layout/AppTopNav.vue`
  Responsibility: render real city options instead of a single current city value and dispatch city switches.
- Modify: `frontend/src/components/__tests__/AppTopNav.spec.js`
  Responsibility: verify city options render and city switching emits state changes.
- Modify: `frontend/src/api/house.js`
  Responsibility: add a dedicated list filter API helper and pass city as a normal filter condition.
- Modify: `frontend/src/views/HouseListView.vue`
  Responsibility: replace hard-coded `locationOptions` with city-driven options, call the list filter API, and stop using `smartGuideHouse` as the main list query.
- Modify: `frontend/src/views/__tests__/HouseListView.spec.js`
  Responsibility: verify region options change with city and list requests carry `city`/`region`.

### Backend

- Modify: `sql/rent-schema/house.sql`
  Responsibility: add `city` and `region` columns plus practical indexes for list filtering.
- Modify: `sql/rent-schema/rent-schema-all.sql`
  Responsibility: keep the aggregate schema in sync with the new `house` columns.
- Modify: `src/main/java/cn/yy/myrent/entity/House.java`
  Responsibility: expose `city` and `region` on the persistence model.
- Modify: `src/main/java/cn/yy/myrent/document/HouseDoc.java`
  Responsibility: mirror `city` and `region` into Elasticsearch documents for future use and consistency.
- Modify: `src/main/java/cn/yy/myrent/vo/HouseVO.java`
  Responsibility: return `city` and `region` to the frontend.
- Create: `src/main/java/cn/yy/myrent/dto/HouseListFilterReqDTO.java`
  Responsibility: define the dedicated list filter request contract.
- Modify: `src/main/java/cn/yy/myrent/service/IHouseService.java`
  Responsibility: declare the new list filter service method.
- Modify: `src/main/java/cn/yy/myrent/controller/HouseController.java`
  Responsibility: expose a dedicated house list filter endpoint.
- Modify: `src/main/java/cn/yy/myrent/mapper/HouseMapper.java`
  Responsibility: declare a MyBatis query for list filtering.
- Modify: `src/main/resources/mapper/HouseMapper.xml`
  Responsibility: implement SQL for city/region/price/rent-type filtering with pagination-friendly ordering.
- Modify: `src/main/java/cn/yy/myrent/service/impl/HouseServiceImpl.java`
  Responsibility: implement the new list filter flow without touching smart-guide behavior.
- Modify: `src/main/java/cn/yy/myrent/service/hot/HouseHotService.java`
  Responsibility: make `HouseVO` hot results carry `city` and `region` so fallback data remains structurally complete.
- Modify: `src/main/java/cn/yy/myrent/sync/house/service/impl/HouseEsSyncServiceImpl.java`
  Responsibility: sync the new fields into ES documents.
- Modify: `src/test/java/cn/yy/myrent/controller/HouseControllerWebMvcTest.java`
  Responsibility: verify the new list filter endpoint contract.
- Create: `src/test/java/cn/yy/myrent/service/impl/HouseServiceImplListFilterTest.java`
  Responsibility: verify service-level filter mapping and VO conversion.

## Task 1: Add shared city/region dictionary config on the frontend

**Files:**
- Create: `frontend/src/config/cityFilters.js`
- Modify: `frontend/src/stores/auth.js`
- Modify: `frontend/src/components/layout/AppTopNav.vue`
- Test: `frontend/src/components/__tests__/AppTopNav.spec.js`

- [ ] **Step 1: Write the failing nav test for city options**

```js
it('renders hot city options and updates current city', async () => {
  const wrapper = mount(AppTopNav, {
    props: {
      items: [{ label: '找房', to: '/houses' }],
      currentPath: '/houses'
    },
    global: {
      stubs: { RouterLink: RouterLinkStub }
    }
  })

  const select = wrapper.get('select.city-select')
  const options = select.findAll('option').map((item) => item.text())

  expect(options).toEqual(['南京', '苏州', '杭州', '上海', '北京', '广州', '深圳', '成都', '武汉'])
})
```

- [ ] **Step 2: Run test to verify it fails**

Run: `npm --prefix frontend run test -- AppTopNav.spec.js`

Expected: FAIL because `AppTopNav` only renders the active city as a single option and no shared city config exists.

- [ ] **Step 3: Add shared config and wire top-nav city switching**

```js
// frontend/src/config/cityFilters.js
export const HOT_CITY_OPTIONS = [
  { label: '南京', value: '南京', regions: ['玄武区', '秦淮区', '建邺区', '鼓楼区', '栖霞区', '江宁区'] },
  { label: '苏州', value: '苏州', regions: ['姑苏区', '虎丘区', '吴中区', '相城区', '吴江区', '工业园区'] },
  { label: '杭州', value: '杭州', regions: ['西湖区', '拱墅区', '上城区', '滨江区', '余杭区', '萧山区'] },
  { label: '上海', value: '上海', regions: ['黄浦区', '徐汇区', '静安区', '浦东新区', '闵行区', '杨浦区'] },
  { label: '北京', value: '北京', regions: ['东城区', '西城区', '朝阳区', '海淀区', '丰台区', '通州区'] },
  { label: '广州', value: '广州', regions: ['天河区', '越秀区', '海珠区', '番禺区', '白云区', '黄埔区'] },
  { label: '深圳', value: '深圳', regions: ['南山区', '福田区', '罗湖区', '宝安区', '龙岗区', '龙华区'] },
  { label: '成都', value: '成都', regions: ['锦江区', '青羊区', '武侯区', '成华区', '高新区', '双流区'] },
  { label: '武汉', value: '武汉', regions: ['江岸区', '江汉区', '硚口区', '洪山区', '武昌区', '东湖高新区'] }
]

export const DEFAULT_CITY = HOT_CITY_OPTIONS[0].value

export function getRegionsByCity(city) {
  return HOT_CITY_OPTIONS.find((item) => item.value === city)?.regions || []
}
```
```

```js
// frontend/src/stores/auth.js
import { DEFAULT_CITY } from '@/config/cityFilters'

state: () => ({
  token: getToken(),
  profile: getProfile() || { city: DEFAULT_CITY }
}),
actions: {
  setCurrentCity(city) {
    this.syncProfile({ city })
  }
}
```

```vue
<!-- frontend/src/components/layout/AppTopNav.vue -->
<select class="city-select" :value="activeCity" aria-label="切换城市" @change="handleCityChange">
  <option v-for="item in HOT_CITY_OPTIONS" :key="item.value" :value="item.value">{{ item.label }}</option>
</select>
```

- [ ] **Step 4: Run test to verify it passes**

Run: `npm --prefix frontend run test -- AppTopNav.spec.js`

Expected: PASS with all nine hot cities rendered in the nav selector.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/config/cityFilters.js frontend/src/stores/auth.js frontend/src/components/layout/AppTopNav.vue frontend/src/components/__tests__/AppTopNav.spec.js
git commit -m "feat: add shared city region filter config"
```

## Task 2: Persist `city` and `region` on house records

**Files:**
- Modify: `sql/rent-schema/house.sql`
- Modify: `sql/rent-schema/rent-schema-all.sql`
- Modify: `src/main/java/cn/yy/myrent/entity/House.java`
- Modify: `src/main/java/cn/yy/myrent/document/HouseDoc.java`
- Modify: `src/main/java/cn/yy/myrent/vo/HouseVO.java`
- Modify: `src/main/java/cn/yy/myrent/sync/house/service/impl/HouseEsSyncServiceImpl.java`
- Modify: `src/main/java/cn/yy/myrent/service/hot/HouseHotService.java`

- [ ] **Step 1: Write the failing service test for city/region mapping**

```java
@Test
void hotHouseConversionShouldCarryCityAndRegion() {
    House house = new House();
    house.setId(1L);
    house.setTitle("姑苏区精装一居");
    house.setCity("苏州");
    house.setRegion("姑苏区");
    house.setPrice(280000);
    house.setDepositAmount(100000);
    house.setStatus(1);

    HouseVO vo = invokeConvertHouseToVo(house);

    assertEquals("苏州", vo.getCity());
    assertEquals("姑苏区", vo.getRegion());
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -Dtest=HouseServiceImplListFilterTest test`

Expected: FAIL because `House`, `HouseVO`, and conversion logic do not yet have `city` or `region`.

- [ ] **Step 3: Add DB columns and model fields**

```sql
-- sql/rent-schema/house.sql
ALTER TABLE house
  ADD COLUMN city VARCHAR(32) NOT NULL DEFAULT '南京' COMMENT '城市',
  ADD COLUMN region VARCHAR(32) NOT NULL DEFAULT '鼓楼区' COMMENT '区域',
  ADD KEY idx_house_city_region_status_price (city, region, status, price);
```

```java
// src/main/java/cn/yy/myrent/entity/House.java
@TableField("city")
private String city;

@TableField("region")
private String region;
```

```java
// src/main/java/cn/yy/myrent/document/HouseDoc.java
@Field(type = FieldType.Keyword)
private String city;

@Field(type = FieldType.Keyword)
private String region;
```

```java
// src/main/java/cn/yy/myrent/vo/HouseVO.java
private String city;
private String region;
```

- [ ] **Step 4: Update ES sync and VO conversion**

```java
// src/main/java/cn/yy/myrent/sync/house/service/impl/HouseEsSyncServiceImpl.java
doc.setCity(house.getCity());
doc.setRegion(house.getRegion());
```

```java
// src/main/java/cn/yy/myrent/service/impl/HouseServiceImpl.java
vo.setCity(house.getCity());
vo.setRegion(house.getRegion());
```

```java
// src/main/java/cn/yy/myrent/service/hot/HouseHotService.java
vo.setCity(house.getCity());
vo.setRegion(house.getRegion());
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `mvn -Dtest=HouseServiceImplListFilterTest test`

Expected: PASS with `city` and `region` now present on converted view objects.

- [ ] **Step 6: Commit**

```bash
git add sql/rent-schema/house.sql sql/rent-schema/rent-schema-all.sql src/main/java/cn/yy/myrent/entity/House.java src/main/java/cn/yy/myrent/document/HouseDoc.java src/main/java/cn/yy/myrent/vo/HouseVO.java src/main/java/cn/yy/myrent/sync/house/service/impl/HouseEsSyncServiceImpl.java src/main/java/cn/yy/myrent/service/hot/HouseHotService.java src/main/java/cn/yy/myrent/service/impl/HouseServiceImpl.java
git commit -m "feat: persist house city and region"
```

## Task 3: Add a dedicated backend list-filter endpoint

**Files:**
- Create: `src/main/java/cn/yy/myrent/dto/HouseListFilterReqDTO.java`
- Modify: `src/main/java/cn/yy/myrent/service/IHouseService.java`
- Modify: `src/main/java/cn/yy/myrent/controller/HouseController.java`
- Modify: `src/main/java/cn/yy/myrent/mapper/HouseMapper.java`
- Modify: `src/main/resources/mapper/HouseMapper.xml`
- Modify: `src/main/java/cn/yy/myrent/service/impl/HouseServiceImpl.java`
- Test: `src/test/java/cn/yy/myrent/controller/HouseControllerWebMvcTest.java`
- Test: `src/test/java/cn/yy/myrent/service/impl/HouseServiceImplListFilterTest.java`

- [ ] **Step 1: Write the failing endpoint test**

```java
@Test
void listFilterShouldAcceptCityAndRegion() throws Exception {
    HouseVO item = new HouseVO();
    item.setId(1L);
    item.setTitle("姑苏区地铁口一居");
    item.setCity("苏州");
    item.setRegion("姑苏区");

    HouseSearchResultVO result = new HouseSearchResultVO();
    result.setHouses(List.of(item));

    given(houseService.filterList(any())).willReturn(result);

    mockMvc.perform(post("/house/list-filter")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {
                              "city": "苏州",
                              "region": "姑苏区",
                              "rentType": 1,
                              "maxPriceYuan": 3500,
                              "page": 1,
                              "size": 8
                            }
                            """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.houses[0].city").value("苏州"))
            .andExpect(jsonPath("$.data.houses[0].region").value("姑苏区"));
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -Dtest=HouseControllerWebMvcTest test`

Expected: FAIL because `/house/list-filter` does not exist and there is no `HouseListFilterReqDTO`.

- [ ] **Step 3: Add the request DTO and controller/service contract**

```java
// src/main/java/cn/yy/myrent/dto/HouseListFilterReqDTO.java
@Data
public class HouseListFilterReqDTO {
    private String city;
    private String region;
    private Integer rentType;
    private Integer minPriceYuan;
    private Integer maxPriceYuan;
    private Integer page = 1;
    private Integer size = 8;
}
```

```java
// src/main/java/cn/yy/myrent/service/IHouseService.java
HouseSearchResultVO filterList(HouseListFilterReqDTO reqDTO);
```

```java
// src/main/java/cn/yy/myrent/controller/HouseController.java
@PostMapping("/list-filter")
public Result<HouseSearchResultVO> listFilter(@RequestBody HouseListFilterReqDTO reqDTO) {
    return Result.success(houseService.filterList(reqDTO));
}
```

- [ ] **Step 4: Implement mapper SQL and service conversion**

```xml
<!-- src/main/resources/mapper/HouseMapper.xml -->
<select id="selectListFilterPage" resultType="cn.yy.myrent.entity.House">
  select *
  from house
  where status = 1
    <if test="city != null and city != ''">and city = #{city}</if>
    <if test="region != null and region != ''">and region = #{region}</if>
    <if test="rentType != null">and rent_type = #{rentType}</if>
    <if test="minPriceCent != null">and price &gt;= #{minPriceCent}</if>
    <if test="maxPriceCent != null">and price &lt;= #{maxPriceCent}</if>
  order by create_time desc, id desc
  limit #{offset}, #{size}
</select>
```

```java
// src/main/java/cn/yy/myrent/service/impl/HouseServiceImpl.java
public HouseSearchResultVO filterList(HouseListFilterReqDTO reqDTO) {
    int page = Math.max(reqDTO.getPage() == null ? 1 : reqDTO.getPage(), 1);
    int size = Math.min(Math.max(reqDTO.getSize() == null ? 8 : reqDTO.getSize(), 1), 50);
    int offset = (page - 1) * size;

    List<House> houses = houseMapper.selectListFilterPage(
            reqDTO.getCity(),
            reqDTO.getRegion(),
            reqDTO.getRentType(),
            yuanToCent(reqDTO.getMinPriceYuan()),
            yuanToCent(reqDTO.getMaxPriceYuan()),
            offset,
            size
    );
    return buildSearchResult(houses.stream().map(this::convertHouseToVo).toList(), false, "DB_FILTER", null);
}
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `mvn -Dtest=HouseControllerWebMvcTest,HouseServiceImplListFilterTest test`

Expected: PASS with controller and service tests confirming city/region-based filtering is wired.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/cn/yy/myrent/dto/HouseListFilterReqDTO.java src/main/java/cn/yy/myrent/service/IHouseService.java src/main/java/cn/yy/myrent/controller/HouseController.java src/main/java/cn/yy/myrent/mapper/HouseMapper.java src/main/resources/mapper/HouseMapper.xml src/main/java/cn/yy/myrent/service/impl/HouseServiceImpl.java src/test/java/cn/yy/myrent/controller/HouseControllerWebMvcTest.java src/test/java/cn/yy/myrent/service/impl/HouseServiceImplListFilterTest.java
git commit -m "feat: add house list filter endpoint"
```

## Task 4: Rewire the house list page to use city-driven regions and the new list filter API

**Files:**
- Modify: `frontend/src/api/house.js`
- Modify: `frontend/src/views/HouseListView.vue`
- Test: `frontend/src/views/__tests__/HouseListView.spec.js`

- [ ] **Step 1: Write the failing house-list view test**

```js
it('updates region options by city and sends city filter to backend', async () => {
  fetchHouseListByFilter.mockResolvedValue({
    houses: [{ id: 1, title: '姑苏区地铁口一居', city: '苏州', region: '姑苏区', price: 3200, status: 1 }]
  })

  const wrapper = await mountView()

  await authStore.setCurrentCity('苏州')
  await nextTick()

  const regionOptions = wrapper.get('[data-test="house-location-select"]').findAll('option').map((item) => item.text())
  expect(regionOptions).toContain('姑苏区')
  expect(regionOptions).not.toContain('天河区')

  await wrapper.get('[data-test="house-location-select"]').setValue('姑苏区')
  await wrapper.get('[data-test="house-price-select"]').setValue('2500-3500')
  await wrapper.get('[data-test="house-rent-mode-select"]').setValue('WHOLE')
  await wrapper.get('[data-test="house-search-submit"]').trigger('click')

  expect(fetchHouseListByFilter).toHaveBeenCalledWith(expect.objectContaining({
    city: '苏州',
    region: '姑苏区'
  }))
})
```

- [ ] **Step 2: Run test to verify it fails**

Run: `npm --prefix frontend run test -- HouseListView.spec.js`

Expected: FAIL because the page still uses hard-coded Guangzhou regions and still calls `smartGuideHouse`.

- [ ] **Step 3: Add the new API helper and page wiring**

```js
// frontend/src/api/house.js
export function fetchHouseListByFilter(payload) {
  return http.post('/house/list-filter', payload)
}
```

```js
// frontend/src/views/HouseListView.vue
import { DEFAULT_CITY, getRegionsByCity } from '@/config/cityFilters'
import { useAuthStore } from '@/stores/auth'
import { fetchHouseListByFilter } from '@/api/house'

const authStore = useAuthStore()
const activeCity = computed(() => authStore.profile?.city || DEFAULT_CITY)
const locationOptions = computed(() =>
  getRegionsByCity(activeCity.value).map((item) => ({ label: item, value: item }))
)
```

```js
// frontend/src/views/HouseListView.vue
watch(activeCity, () => {
  filters.locationName = ''
  lastGuidePayload.value = null
  loadFeaturedHouses()
})
```

```js
// frontend/src/views/HouseListView.vue
const payload = {
  city: activeCity.value,
  region: filters.locationName,
  rentType: filters.rentMode === 'WHOLE' ? 1 : 2,
  maxPriceYuan: Number(selectedPriceOption.value?.budget || 3000),
  page: 1,
  size: 8
}
const result = await fetchHouseListByFilter(payload)
```

- [ ] **Step 4: Replace city-agnostic mock generation with active-city regions**

```js
function buildMockFeaturedHouses() {
  const regions = getRegionsByCity(activeCity.value)
  return [2, 5, 8, 11, 14].map((seed, index) =>
    normalizeHouseRecord(
      {
        id: seed,
        title: `${regions[index % regions.length]}精装公寓`,
        city: activeCity.value,
        region: regions[index % regions.length],
        price: 1980 + index * 420,
        rentalType: index % 2 === 0 ? '整租' : '合租',
        status: 1
      },
      index,
      { source: 'featured', city: activeCity.value }
    )
  )
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `npm --prefix frontend run test -- HouseListView.spec.js`

Expected: PASS with city-aware region options and list-filter API requests.

- [ ] **Step 6: Commit**

```bash
git add frontend/src/api/house.js frontend/src/views/HouseListView.vue frontend/src/views/__tests__/HouseListView.spec.js
git commit -m "feat: connect house list page to structured filters"
```

## Task 5: Verify the foundation and document the handoff

**Files:**
- Modify: `docs/superpowers/plans/2026-04-25-house-list-filter-foundation.md`

- [ ] **Step 1: Run backend tests**

Run: `mvn -Dtest=HouseControllerWebMvcTest,HouseServiceImplListFilterTest test`

Expected: PASS with list-filter request/response and conversion behavior covered.

- [ ] **Step 2: Run frontend tests**

Run: `npm --prefix frontend run test -- AppTopNav.spec.js HouseListView.spec.js`

Expected: PASS with city switching and region filtering covered.

- [ ] **Step 3: Run one manual smoke check**

Run:

```bash
npm --prefix frontend run dev
```

Expected:
- top nav city selector shows all nine hot cities
- switching city resets the region selector
- selecting `苏州 -> 姑苏区` no longer shows Guangzhou districts
- filtered result cards display `city` and `region` consistently

- [ ] **Step 4: Record follow-up work explicitly**

```md
Deferred after this plan:
- location-based search input using `location_dict`
- combined text + place concurrent recall
- smart-guide repositioning as recommendation, not primary list filtering
```

- [ ] **Step 5: Commit**

```bash
git add docs/superpowers/plans/2026-04-25-house-list-filter-foundation.md
git commit -m "docs: capture house list filter foundation plan"
```

## Self-Review

### Spec coverage

- Hot city config on frontend: covered in Task 1.
- `house` persistence needs `city` and `region`: covered in Task 2.
- Dedicated list filtering instead of smart-guide reuse: covered in Task 3 and Task 4.
- City-dependent region options on the list page: covered in Task 4.
- Verification path and future separation from place search: covered in Task 5.

### Placeholder scan

- No `TODO`, `TBD`, or “implement later” placeholders remain in tasks.
- Deferred scope is explicitly named and intentionally left out of this implementation batch.

### Type consistency

- Shared naming is `city` + `region` across frontend config, DB entity, ES doc, and VO.
- Dedicated list filter contract is `HouseListFilterReqDTO` and `filterList`, separate from `SmartGuideReqDTO` and `smartGuide`.

## Recommended Implementation Order

1. Frontend city dictionary and top-nav switching.
2. DB/model/VO `city` + `region` fields.
3. Dedicated backend list-filter endpoint.
4. House list page rewiring and tests.
5. Verification and only then move on to place search.

## Notes For This Repo

- Do not modify `smart-guide` behavior in this plan.
- Do not introduce Redis for city/region dictionary data.
- Keep the hot-city dictionary in frontend config for demo control, but keep `city` and `region` as persisted backend truth on each `house`.

Plan complete and saved to `docs/superpowers/plans/2026-04-25-house-list-filter-foundation.md`. Two execution options:

**1. Subagent-Driven (recommended)** - I dispatch a fresh subagent per task, review between tasks, fast iteration

**2. Inline Execution** - Execute tasks in this session using executing-plans, batch execution with checkpoints

Which approach?
