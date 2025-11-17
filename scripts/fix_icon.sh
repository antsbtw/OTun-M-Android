#!/bin/bash

# =============================================================================
# Fix Android Adaptive Icon
# =============================================================================
# 用途：移除或替换 Adaptive Icon 配置，使用普通图标
# =============================================================================

set -e

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

PROJECT_DIR="$HOME/Documents/VPN-Server/OTun-M-Android/sing-box-for-android"

print_step() {
    echo -e "${GREEN}▶ $1${NC}"
}

print_success() {
    echo -e "${GREEN}✓ $1${NC}"
}

echo -e "${BLUE}=== 修复 Android 图标显示问题 ===${NC}"
echo ""

cd "$PROJECT_DIR"

# 方案 1: 直接禁用 Adaptive Icon（最简单）
print_step "方案 1: 禁用 Adaptive Icon，使用普通图标"
echo ""
read -p "是否使用此方案？系统将使用 mipmap-* 下的 PNG 图标 (推荐) (y/n) " -n 1 -r
echo ""

if [[ $REPLY =~ ^[Yy]$ ]]; then
    print_step "1. 备份 Adaptive Icon 配置..."
    if [ -d "app/src/main/res/mipmap-anydpi-v26" ]; then
        # 创建备份目录（在 app/ 下，不在 res/ 下）
        mkdir -p app/backup_icons
        mv app/src/main/res/mipmap-anydpi-v26 \
           app/backup_icons/mipmap-anydpi-v26_$(date +%Y%m%d_%H%M%S)
        print_success "已禁用 Adaptive Icon（备份在 app/backup_icons/）"
    else
        print_success "Adaptive Icon 已经被禁用"
    fi
    
    # 清理可能存在的错误备份
    print_step "1.1 清理错误的备份目录..."
    rm -rf app/src/main/res/mipmap-anydpi-v26.backup_* 2>/dev/null || true
    print_success "已清理"
    
    print_step "2. 完全卸载应用..."
    adb uninstall com.situstechnologies.OXray 2>/dev/null || true
    
    print_step "3. 清理构建缓存..."
    ./gradlew clean
    rm -rf app/build
    
    print_step "4. 重新构建..."
    ./gradlew assembleOtherDebug
    
    print_step "5. 安装..."
    ./gradlew installOtherDebug
    
    print_step "6. 清除启动器缓存..."
    # 清除各种常见启动器的缓存
    adb shell pm clear com.android.launcher3 2>/dev/null || true
    adb shell pm clear com.samsung.android.app.launcher 2>/dev/null || true
    adb shell pm clear com.miui.home 2>/dev/null || true
    adb shell pm clear com.huawei.android.launcher 2>/dev/null || true
    
    echo ""
    print_success "完成！"
    echo ""
    echo -e "${YELLOW}如果图标仍未更新，请尝试：${NC}"
    echo "  1. 重启设备"
    echo "  2. 或在启动器长按图标 → 删除 → 重新从应用列表添加"
    echo ""
    
else
    echo ""
    print_step "方案 2: 创建新的 Adaptive Icon 前景图"
    echo ""
    echo "需要创建 ic_launcher_foreground 资源。"
    echo "请参考后续步骤手动创建，或使用 Android Studio 的 Image Asset 工具。"
fi