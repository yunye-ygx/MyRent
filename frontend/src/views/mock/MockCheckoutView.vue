<template>
  <div class="page">
    <section class="card">
      <h2 class="section-title">模拟支付</h2>
      <p>订单号：{{ checkout?.orderNo }}</p>
      <p>支付单号：{{ checkout?.paymentNo }}</p>
      <p>金额：{{ formatPrice(checkout?.amount) }}</p>
      <p>剩余支付时间：{{ remainingText }}</p>
      <div class="actions">
        <button class="primary-btn" @click="paySuccess">支付成功</button>
        <button class="ghost-btn" @click="cancelOrder">取消订单</button>
      </div>
    </section>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { fetchMockCheckout, submitMockCallback } from '@/api/payment'
import { formatPrice } from '@/utils/format'

const route = useRoute()
const router = useRouter()
const checkout = ref(null)

const remainingText = computed(() => `${Math.max(Number(checkout.value?.remainingSeconds || 0), 0)} 秒`)

async function loadCheckout() {
  checkout.value = await fetchMockCheckout(route.query.paymentNo)
}

async function paySuccess() {
  await submitMockCallback({
    orderNo: checkout.value.orderNo,
    paymentNo: checkout.value.paymentNo,
    thirdPartyTradeNo: `TP-${Date.now()}`,
    callbackNo: `CB-${Date.now()}`,
    payStatus: 'SUCCESS',
    payAmount: checkout.value.amount,
    callbackTime: new Date().toISOString()
  })
  await router.replace('/mine/orders')
}

async function cancelOrder() {
  await submitMockCallback({
    orderNo: checkout.value.orderNo,
    paymentNo: checkout.value.paymentNo,
    thirdPartyTradeNo: '',
    callbackNo: `CB-${Date.now()}`,
    payStatus: 'CANCELLED',
    payAmount: checkout.value.amount,
    callbackTime: new Date().toISOString()
  })
  await router.replace('/mine/orders')
}

onMounted(loadCheckout)
</script>
