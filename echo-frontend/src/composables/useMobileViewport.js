import { onBeforeUnmount, onMounted, ref } from "vue";

const MOBILE_QUERY = "(max-width: 768px)";

/**
 * Keeps page-level responsive behavior consistent with the shared 768px CSS breakpoint.
 */
export function useMobileViewport() {
  const isMobile = ref(false);
  let mediaQueryList;

  const update = () => {
    isMobile.value = Boolean(mediaQueryList?.matches);
  };

  onMounted(() => {
    mediaQueryList = window.matchMedia(MOBILE_QUERY);
    update();
    mediaQueryList.addEventListener("change", update);
  });

  onBeforeUnmount(() => {
    mediaQueryList?.removeEventListener("change", update);
  });

  return { isMobile };
}
