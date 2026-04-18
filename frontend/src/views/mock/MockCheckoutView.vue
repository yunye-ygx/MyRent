<template>
  <div class="page">
    <section class="card">
      <h2 class="section-title">Mock Checkout</h2>
      <p>Order No: {{ checkout?.orderNo }}</p>
      <p>Payment No: {{ checkout?.paymentNo }}</p>
      <p>Amount: {{ formatPrice(checkout?.amount) }}</p>
      <p>Remaining: {{ remainingText }}</p>
      <div class="actions">
        <button class="primary-btn" @click="paySuccess">Pay Success</button>
        <button class="ghost-btn" @click="cancelOrder">Cancel Order</button>
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

const remainingText = computed(() => `${Math.max(Number(checkout.value?.remainingSeconds || 0), 0)} s`)

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
