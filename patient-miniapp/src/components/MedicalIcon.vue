<template>
  <image class="medical-icon" :src="iconSrc" mode="aspectFit" :style="iconStyle" />
</template>

<script setup lang="ts">
import { computed } from 'vue';
import { getMedicalIcon, type MedicalIconName, type MedicalIconVariant } from '../constants/medical-icons';

const props = withDefaults(
  defineProps<{
    name: MedicalIconName;
    size?: number;
    variant?: MedicalIconVariant;
  }>(),
  {
    size: 30,
    variant: 'dark'
  }
);

const iconSrc = computed(() => getMedicalIcon(props.name, props.variant));
const iconStyle = computed(() => `width: ${props.size}px; height: ${props.size}px;`);
</script>

<style scoped>
.medical-icon {
  display: block;
}
</style>
