<template>
    <div>
        <el-dialog
            :show-close="showClose"
            :draggable="true"
            :model-value="show"
            :close-on-click-modal="false"
            :title="title"
            class="cust-dialog"
            :top="top + 'px'"
            :width="width"
            @close="close"
        >
        <div
            class="dialog-body"
            :style="{ 'max-height': maxHeight + 'px', padding: padding + 'px' }"
        >
            <slot></slot>
    </div>
    <template v-if="(buttons && buttons.length > 0) || showCancel">
        <div class="dialog-footer">
            <el-button  v-if="showCancel" link @click="close"> 取消 </el-button>
            <el-button
                v-for="(btn, index) in buttons"
                :key="index"
                class="dialog-buttons"
                :type="btn.type || 'primary' "
                @click="btn.click"
            >
                {{ btn.text }}
        </el-button>
        </div>
    </template>
    </el-dialog>
    </div>
</template>

<script setup>
const props = defineProps ({
    title: {
        type: String,
    },
    show: {
        type: Boolean,
        default: false,
    },
    showClose: {
        type: Boolean,
        default: true,
    },
    showCancel: {
        type: Boolean,
        default: true,
    },
    top: {
        type: Number,
        default: 50,
    },
    width: {
        type: String,
        default: "30%",
    },
    buttons: {
        type: Array,
    },
    padding: {
        type: Number,
        default: 15,
    },
});

const maxHeight = window.innerHeight - props.top - 100;

const emit = defineEmits(["close"]);
const close = () => {
    emit("close");
};
</script>

<style lang="scss">
.cust-dialog {
    margin: 40px auto 12px !important;
    border-radius: 20px;
    overflow: hidden;
    border: 1px solid rgba(194, 204, 220, 0.84);
    box-shadow: var(--shadow-lg);
    background: rgba(255, 255, 255, 0.88);
    backdrop-filter: blur(10px);
    
    .el-dialog__header {
        padding: 18px 20px 14px;
        border-bottom: 1px solid rgba(194, 204, 220, 0.65);
        margin-right: 0;
        
        .el-dialog__title {
            font-size: 17px;
            letter-spacing: 0.04em;
            font-family: var(--font-heading);
            font-weight: 700;
            color: var(--text-main);
        }
        
        .el-dialog__headerbtn {
            top: 18px;
        }
    }
    
    .el-dialog__body {
        padding: 0;
    }
    
    .dialog-body {
        padding: 18px !important;
        min-height: 80px;
        overflow: auto;
    }
    
    .dialog-footer {
        text-align: right;
        padding: 14px 20px;
        border-top: 1px solid rgba(194, 204, 220, 0.65);
        background: rgba(246, 248, 255, 0.8);
    }
    
    .dialog-buttons {
        font-weight: 600;
    }
}
</style>
