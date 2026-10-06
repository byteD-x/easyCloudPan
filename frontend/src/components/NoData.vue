<template>
    <div class="no-data">
        <div>
            <Icon icon-name="no_data" :width="120" fit="fill"></Icon>
        </div>
        <div class="msg">{{ msg }}</div>
    </div>
</template>

<script setup>
defineProps({
    msg: {
        type: String,
    },
});
</script>

<style lang="scss" scoped>
.no-data {
    text-align: center;
    padding: 30px 0;
    
    .icon-empty {
        font-size: 56px;
        color: var(--primary-light);
    }
    
    .msg {
        margin-top: 12px;
        color: var(--text-secondary);
        font-size: 13px;
        letter-spacing: 0.03em;
    }
}
</style>
