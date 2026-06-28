<template>
  <image class="medical-icon" :src="iconSrc" mode="aspectFit" :style="iconStyle" />
</template>

<script setup lang="ts">
import { computed } from 'vue';

type MedicalIconName =
  | 'book-open-text'
  | 'calendar-days'
  | 'clipboard-list'
  | 'flask-conical'
  | 'hospital'
  | 'microscope'
  | 'pill-bottle'
  | 'stethoscope'
  | 'syringe'
  | 'user-round-plus'
  | 'wallet-cards';

const props = withDefaults(
  defineProps<{
    name: MedicalIconName;
    size?: number;
    variant?: 'dark' | 'white';
  }>(),
  {
    size: 30,
    variant: 'dark'
  }
);

const iconSrc = computed(() => {
  const folder = props.variant === 'white' ? 'icons/white' : 'icons';
  return `/static/${folder}/${props.name}.svg`;
});
const iconStyle = computed(() => `width: ${props.size}px; height: ${props.size}px;`);
</script>

<style scoped>
.medical-icon {
  display: block;
}
</style>
