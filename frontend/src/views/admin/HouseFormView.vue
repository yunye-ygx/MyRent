<template>
  <div style="max-width:720px;">
    <h2 style="margin:0 0 20px; font-size:18px; color:#333;">{{ isEdit ? '编辑房源' : '发布房源' }}</h2>
    <el-form :model="form" :rules="rules" ref="formRef" label-width="100px" v-loading="loading">
      <el-form-item label="标题" prop="title">
        <el-input v-model="form.title" placeholder="房源标题" />
      </el-form-item>
      <el-row :gutter="16">
        <el-col :span="12">
          <el-form-item label="城市" prop="city">
            <el-input v-model="form.city" placeholder="如：上海" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="区域" prop="region">
            <el-input v-model="form.region" placeholder="如：浦东新区" />
          </el-form-item>
        </el-col>
      </el-row>
      <el-row :gutter="16">
        <el-col :span="12">
          <el-form-item label="月租金(元)" prop="priceYuan">
            <el-input-number v-model="form.priceYuan" :min="1" :precision="0" style="width:100%;" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="押金(元)" prop="depositYuan">
            <el-input-number v-model="form.depositYuan" :min="0" :precision="0" style="width:100%;" />
          </el-form-item>
        </el-col>
      </el-row>
      <el-row :gutter="16">
        <el-col :span="12">
          <el-form-item label="经度" prop="longitude">
            <el-input v-model="form.longitude" placeholder="如：121.4737" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="纬度" prop="latitude">
            <el-input v-model="form.latitude" placeholder="如：31.2304" />
          </el-form-item>
        </el-col>
      </el-row>
      <el-form-item label="租型" prop="rentType">
        <el-select v-model="form.rentType" style="width:100%;">
          <el-option label="整租" :value="1" />
          <el-option label="合租" :value="2" />
        </el-select>
      </el-form-item>
      <el-form-item label="配套设施">
        <el-checkbox v-model="form.nearSubway" :true-value="1" :false-value="0">近地铁</el-checkbox>
        <el-checkbox v-model="form.privateBathroom" :true-value="1" :false-value="0">独卫</el-checkbox>
        <el-checkbox v-model="form.hasBalcony" :true-value="1" :false-value="0">有阳台</el-checkbox>
        <el-checkbox v-model="form.civilWaterElectric" :true-value="1" :false-value="0">民水民电</el-checkbox>
        <el-checkbox v-model="form.supportStudentDepositFree" :true-value="1" :false-value="0">学生免押</el-checkbox>
      </el-form-item>
      <el-form-item label="发布者ID" prop="publisherUserId">
        <el-input-number v-model="form.publisherUserId" :min="1" style="width:100%;" />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="handleSubmit" :loading="submitting">{{ isEdit ? '保存修改' : '发布房源' }}</el-button>
        <el-button @click="$router.push('/admin/houses')">取消</el-button>
      </el-form-item>
    </el-form>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { createAdminHouse, updateAdminHouse } from '@/api/admin'
import { fetchHouseById } from '@/api/house'
import { useAuthStore } from '@/stores/auth'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const formRef = ref()
const loading = ref(false)
const submitting = ref(false)
const isEdit = computed(() => !!route.params.id)

const form = ref({
  title: '', city: '', region: '',
  priceYuan: 1000, depositYuan: 1000,
  longitude: '', latitude: '',
  rentType: 1,
  nearSubway: 0, privateBathroom: 0, hasBalcony: 0,
  civilWaterElectric: 0, supportStudentDepositFree: 0,
  publisherUserId: authStore.userId
})

const rules = {
  title: [{ required: true, message: '请输入标题' }],
  city: [{ required: true, message: '请输入城市' }],
  region: [{ required: true, message: '请输入区域' }],
  longitude: [{ required: true, message: '请输入经度' }],
  latitude: [{ required: true, message: '请输入纬度' }],
  rentType: [{ required: true, message: '请选择租型' }],
  publisherUserId: [{ required: true, message: '请输入发布者ID' }]
}

onMounted(async () => {
  if (!isEdit.value) return
  loading.value = true
  try {
    const house = await fetchHouseById(route.params.id)
    Object.assign(form.value, {
      ...house,
      priceYuan: Math.round(house.price / 100),
      depositYuan: Math.round(house.depositAmount / 100)
    })
  } finally { loading.value = false }
})

async function handleSubmit() {
  await formRef.value.validate()
  submitting.value = true
  try {
    const payload = {
      ...form.value,
      price: Math.round(form.value.priceYuan * 100),
      depositAmount: Math.round(form.value.depositYuan * 100)
    }
    if (isEdit.value) {
      await updateAdminHouse(route.params.id, payload)
      ElMessage.success('修改成功')
    } else {
      await createAdminHouse(payload)
      ElMessage.success('发布成功')
    }
    router.push('/admin/houses')
  } finally { submitting.value = false }
}
</script>
