#!/bin/bash

# =============================================================================
# Android Package Renaming Script
# =============================================================================
# 用途：自动化修改 Android 项目的包名
# 作者：Claude AI Assistant
# 日期：2024
# =============================================================================

set -e  # 遇到错误立即退出

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# 配置
OLD_PACKAGE="io.nekohasekai.sfa"
NEW_PACKAGE="com.situstechnologies.OXray"
OLD_PATH="io/nekohasekai/sfa"
NEW_PATH="com/situstechnologies/OXray"

# 项目根目录（脚本所在目录的上一级）
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"
BACKUP_DIR="${PROJECT_ROOT}/backup_$(date +%Y%m%d_%H%M%S)"

# =============================================================================
# 辅助函数
# =============================================================================

print_header() {
    echo ""
    echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
    echo -e "${BLUE}  $1${NC}"
    echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
    echo ""
}

print_step() {
    echo -e "${GREEN}▶ $1${NC}"
}

print_warning() {
    echo -e "${YELLOW}⚠ $1${NC}"
}

print_error() {
    echo -e "${RED}✗ $1${NC}"
}

print_success() {
    echo -e "${GREEN}✓ $1${NC}"
}

# =============================================================================
# 检查函数
# =============================================================================

check_requirements() {
    print_header "检查环境要求"
    
    # 检查是否在项目根目录
    if [ ! -f "${PROJECT_ROOT}/settings.gradle" ] && [ ! -f "${PROJECT_ROOT}/settings.gradle.kts" ]; then
        print_error "错误：未找到 settings.gradle，请确保在项目根目录运行此脚本"
        exit 1
    fi
    
    # 检查必要的命令
    local commands=("sed" "find" "grep" "git")
    for cmd in "${commands[@]}"; do
        if ! command -v "$cmd" &> /dev/null; then
            print_error "错误：未找到命令 '$cmd'，请先安装"
            exit 1
        fi
    done
    
    print_success "环境检查通过"
}

check_git_status() {
    print_header "检查 Git 状态"
    
    cd "$PROJECT_ROOT"
    
    if [ -d ".git" ]; then
        if [ -n "$(git status --porcelain)" ]; then
            print_warning "工作区有未提交的更改"
            read -p "是否继续？(y/n) " -n 1 -r
            echo
            if [[ ! $REPLY =~ ^[Yy]$ ]]; then
                exit 1
            fi
        fi
        print_success "Git 状态检查完成"
    else
        print_warning "未检测到 Git 仓库"
    fi
}

# =============================================================================
# 备份函数
# =============================================================================

create_backup() {
    print_header "创建备份"
    
    print_step "备份目录: ${BACKUP_DIR}"
    
    mkdir -p "$BACKUP_DIR"
    
    # 备份关键文件和目录
    print_step "备份 app/ 目录..."
    cp -r "${PROJECT_ROOT}/app" "${BACKUP_DIR}/"
    
    print_step "备份 build.gradle 文件..."
    find "$PROJECT_ROOT" -maxdepth 2 -name "build.gradle*" -exec cp {} "${BACKUP_DIR}/" \;
    
    print_step "备份 settings.gradle..."
    find "$PROJECT_ROOT" -maxdepth 1 -name "settings.gradle*" -exec cp {} "${BACKUP_DIR}/" \;
    
    print_success "备份完成: ${BACKUP_DIR}"
}

# =============================================================================
# 主要修改函数
# =============================================================================

update_build_gradle() {
    print_header "修改 build.gradle 文件"
    
    local gradle_file="${PROJECT_ROOT}/app/build.gradle"
    local gradle_kts_file="${PROJECT_ROOT}/app/build.gradle.kts"
    
    if [ -f "$gradle_kts_file" ]; then
        print_step "处理 build.gradle.kts..."
        
        # 使用 | 作为分隔符避免路径中的 / 冲突
        sed -i '' "s|${OLD_PACKAGE}|${NEW_PACKAGE}|g" "$gradle_kts_file"
        
        # 确保有 namespace 配置
        if ! grep -q "namespace" "$gradle_kts_file"; then
            print_warning "未找到 namespace 配置，需要手动添加"
        fi
        
    elif [ -f "$gradle_file" ]; then
        print_step "处理 build.gradle..."
        
        sed -i '' "s|${OLD_PACKAGE}|${NEW_PACKAGE}|g" "$gradle_file"
        
        if ! grep -q "namespace" "$gradle_file"; then
            print_warning "未找到 namespace 配置，需要手动添加"
        fi
    else
        print_error "未找到 build.gradle 文件"
        exit 1
    fi
    
    print_success "build.gradle 修改完成"
}

update_manifest() {
    print_header "修改 AndroidManifest.xml"
    
    local manifest_files=$(find "${PROJECT_ROOT}/app/src" -name "AndroidManifest.xml")
    
    for manifest in $manifest_files; do
        print_step "处理: $manifest"
        sed -i '' "s|${OLD_PACKAGE}|${NEW_PACKAGE}|g" "$manifest"
    done
    
    print_success "AndroidManifest.xml 修改完成"
}

update_kotlin_files() {
    print_header "修改 Kotlin 源代码"
    
    local kt_files=$(find "${PROJECT_ROOT}/app/src" -name "*.kt" -type f)
    local count=0
    
    print_step "查找所有 .kt 文件..."
    
    for file in $kt_files; do
        # 替换 package 声明
        sed -i '' "s|^package ${OLD_PACKAGE}|package ${NEW_PACKAGE}|g" "$file"
        
        # 替换 import 语句
        sed -i '' "s|import ${OLD_PACKAGE}\.|import ${NEW_PACKAGE}.|g" "$file"
        
        # 替换完整类名引用
        sed -i '' "s|${OLD_PACKAGE}\.|${NEW_PACKAGE}.|g" "$file"
        
        ((count++))
    done
    
    print_success "已处理 ${count} 个 Kotlin 文件"
}

update_xml_resources() {
    print_header "修改 XML 资源文件"
    
    local xml_files=$(find "${PROJECT_ROOT}/app/src/main/res" -name "*.xml" -type f 2>/dev/null)
    local count=0
    
    print_step "查找所有 XML 资源文件..."
    
    for file in $xml_files; do
        # 替换自定义 View 的包名
        sed -i '' "s|${OLD_PACKAGE}\.|${NEW_PACKAGE}.|g" "$file"
        
        ((count++))
    done
    
    print_success "已处理 ${count} 个 XML 文件"
}

update_proguard_rules() {
    print_header "修改 ProGuard 规则"
    
    local proguard_file="${PROJECT_ROOT}/app/proguard-rules.pro"
    
    if [ -f "$proguard_file" ]; then
        print_step "处理 proguard-rules.pro..."
        sed -i '' "s|${OLD_PACKAGE}|${NEW_PACKAGE}|g" "$proguard_file"
        print_success "ProGuard 规则修改完成"
    else
        print_warning "未找到 proguard-rules.pro 文件"
    fi
}

update_aidl_files() {
    print_header "修改 AIDL 文件"
    
    local aidl_files=$(find "${PROJECT_ROOT}/app/src/main/aidl" -name "*.aidl" -type f 2>/dev/null)
    local count=0
    
    if [ -z "$aidl_files" ]; then
        print_warning "未找到 AIDL 文件"
        return
    fi
    
    print_step "查找所有 .aidl 文件..."
    
    for file in $aidl_files; do
        # 替换 package 声明
        sed -i '' "s|^package ${OLD_PACKAGE}|package ${NEW_PACKAGE}|g" "$file"
        
        # 替换 import 语句
        sed -i '' "s|import ${OLD_PACKAGE}\.|import ${NEW_PACKAGE}.|g" "$file"
        
        ((count++))
    done
    
    print_success "已处理 ${count} 个 AIDL 文件"
}

move_source_directories() {
    print_header "移动源代码目录"
    
    # 处理 Java/Kotlin 源代码目录
    local src_dirs=$(find "${PROJECT_ROOT}/app/src" -type d -path "*/java/${OLD_PATH}" 2>/dev/null)
    
    for old_dir in $src_dirs; do
        # 找到 java 目录的位置
        local java_dir=$(echo "$old_dir" | sed "s|/${OLD_PATH}||")
        local new_dir="${java_dir}/${NEW_PATH}"
        
        print_step "移动 Java 源码:"
        print_step "  从: $old_dir"
        print_step "  到: $new_dir"
        
        # 创建新目录结构
        mkdir -p "$new_dir"
        
        # 移动文件
        if [ -d "$old_dir" ]; then
            cp -r "$old_dir"/* "$new_dir/" 2>/dev/null || true
            
            # 删除旧目录及其空父目录
            rm -rf "$old_dir"
            
            # 清理空的父目录
            local old_root=$(dirname "$old_dir")
            while [ "$old_root" != "$java_dir" ] && [ -d "$old_root" ]; do
                if [ -z "$(ls -A "$old_root")" ]; then
                    rm -rf "$old_root"
                    old_root=$(dirname "$old_root")
                else
                    break
                fi
            done
        fi
    done
    
    # 处理 AIDL 目录
    local aidl_dirs=$(find "${PROJECT_ROOT}/app/src" -type d -path "*/aidl/${OLD_PATH}" 2>/dev/null)
    
    for old_dir in $aidl_dirs; do
        # 找到 aidl 目录的位置
        local aidl_dir=$(echo "$old_dir" | sed "s|/${OLD_PATH}||")
        local new_dir="${aidl_dir}/${NEW_PATH}"
        
        print_step "移动 AIDL 文件:"
        print_step "  从: $old_dir"
        print_step "  到: $new_dir"
        
        # 创建新目录结构
        mkdir -p "$new_dir"
        
        # 移动文件
        if [ -d "$old_dir" ]; then
            cp -r "$old_dir"/* "$new_dir/" 2>/dev/null || true
            
            # 删除旧目录及其空父目录
            rm -rf "$old_dir"
            
            # 清理空的父目录
            local old_root=$(dirname "$old_dir")
            while [ "$old_root" != "$aidl_dir" ] && [ -d "$old_root" ]; do
                if [ -z "$(ls -A "$old_root")" ]; then
                    rm -rf "$old_root"
                    old_root=$(dirname "$old_root")
                else
                    break
                fi
            done
        fi
    done
    
    print_success "源代码目录移动完成"
}

# =============================================================================
# 验证函数
# =============================================================================

verify_changes() {
    print_header "验证修改结果"
    
    local old_refs=$(grep -r "${OLD_PACKAGE}" "${PROJECT_ROOT}/app/src" 2>/dev/null | wc -l)
    local new_refs=$(grep -r "${NEW_PACKAGE}" "${PROJECT_ROOT}/app/src" 2>/dev/null | wc -l)
    
    print_step "旧包名引用数量: ${old_refs}"
    print_step "新包名引用数量: ${new_refs}"
    
    if [ "$old_refs" -gt 0 ]; then
        print_warning "警告：仍然存在 ${old_refs} 处旧包名引用"
        echo ""
        echo "引用位置："
        grep -rn "${OLD_PACKAGE}" "${PROJECT_ROOT}/app/src" 2>/dev/null | head -n 10
        echo ""
    else
        print_success "未发现旧包名引用"
    fi
    
    # 检查 Java/Kotlin 目录结构
    if [ -d "${PROJECT_ROOT}/app/src/main/java/${NEW_PATH}" ]; then
        print_success "新 Java 目录结构已创建"
    else
        print_warning "警告：未找到新 Java 目录结构"
    fi
    
    if [ -d "${PROJECT_ROOT}/app/src/main/java/${OLD_PATH}" ]; then
        print_warning "警告：旧 Java 目录结构仍然存在"
    else
        print_success "旧 Java 目录结构已清理"
    fi
    
    # 检查 AIDL 目录结构
    if [ -d "${PROJECT_ROOT}/app/src/main/aidl" ]; then
        if [ -d "${PROJECT_ROOT}/app/src/main/aidl/${NEW_PATH}" ]; then
            print_success "新 AIDL 目录结构已创建"
        fi
        
        if [ -d "${PROJECT_ROOT}/app/src/main/aidl/${OLD_PATH}" ]; then
            print_warning "警告：旧 AIDL 目录结构仍然存在"
        else
            print_success "旧 AIDL 目录结构已清理"
        fi
    fi
}

clean_build() {
    print_header "清理构建缓存"
    
    cd "$PROJECT_ROOT"
    
    print_step "清理 build/ 目录..."
    rm -rf build app/build
    
    print_step "清理 .gradle/ 缓存..."
    rm -rf .gradle
    
    print_success "构建缓存清理完成"
}

test_build() {
    print_header "测试编译"
    
    cd "$PROJECT_ROOT"
    
    print_step "运行 ./gradlew clean..."
    ./gradlew clean
    
    print_step "运行 ./gradlew assembleOtherDebug..."
    if ./gradlew assembleOtherDebug; then
        print_success "编译成功！"
        return 0
    else
        print_error "编译失败！"
        return 1
    fi
}

# =============================================================================
# 回滚函数
# =============================================================================

rollback() {
    print_header "回滚更改"
    
    if [ ! -d "$BACKUP_DIR" ]; then
        print_error "未找到备份目录: ${BACKUP_DIR}"
        exit 1
    fi
    
    print_step "从备份恢复文件..."
    
    # 恢复 app/ 目录
    if [ -d "${BACKUP_DIR}/app" ]; then
        rm -rf "${PROJECT_ROOT}/app"
        cp -r "${BACKUP_DIR}/app" "${PROJECT_ROOT}/"
    fi
    
    # 恢复 build.gradle 文件
    cp "${BACKUP_DIR}"/build.gradle* "${PROJECT_ROOT}/" 2>/dev/null || true
    cp "${BACKUP_DIR}"/build.gradle* "${PROJECT_ROOT}/app/" 2>/dev/null || true
    
    print_success "回滚完成"
}

# =============================================================================
# 主流程
# =============================================================================

show_summary() {
    print_header "修改摘要"
    
    echo -e "${BLUE}旧包名:${NC} ${OLD_PACKAGE}"
    echo -e "${BLUE}新包名:${NC} ${NEW_PACKAGE}"
    echo -e "${BLUE}项目路径:${NC} ${PROJECT_ROOT}"
    echo -e "${BLUE}备份位置:${NC} ${BACKUP_DIR}"
    echo ""
}

main() {
    clear
    
    print_header "Android 包名重构工具"
    
    echo -e "${YELLOW}此脚本将会：${NC}"
    echo "  1. 修改 build.gradle 中的 namespace 和 applicationId"
    echo "  2. 修改 AndroidManifest.xml 中的 package"
    echo "  3. 修改所有 Kotlin 文件的 package 声明"
    echo "  4. 修改所有 import 语句"
    echo "  5. 修改 XML 资源文件中的包名引用"
    echo "  6. 修改 AIDL 文件的 package 声明"
    echo "  7. 移动源代码和 AIDL 目录结构"
    echo "  8. 修改 ProGuard 规则"
    echo ""
    echo -e "${YELLOW}旧包名:${NC} ${OLD_PACKAGE}"
    echo -e "${YELLOW}新包名:${NC} ${NEW_PACKAGE}"
    echo ""
    
    read -p "是否继续？(y/n) " -n 1 -r
    echo
    if [[ ! $REPLY =~ ^[Yy]$ ]]; then
        echo "已取消"
        exit 0
    fi
    
    # 执行检查
    check_requirements
    check_git_status
    
    # 创建备份
    create_backup
    
    # 执行修改
    update_build_gradle
    update_manifest
    update_kotlin_files
    update_xml_resources
    update_aidl_files
    update_proguard_rules
    move_source_directories
    
    # 验证
    verify_changes
    
    # 清理
    clean_build
    
    # 显示摘要
    show_summary
    
    echo ""
    echo -e "${GREEN}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
    echo -e "${GREEN}  包名重构完成！${NC}"
    echo -e "${GREEN}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
    echo ""
    echo "下一步操作："
    echo "  1. 运行 './gradlew assembleOtherDebug' 测试编译"
    echo "  2. 检查生成的 APK 包名"
    echo "  3. 运行应用进行完整测试"
    echo ""
    echo "如需回滚，运行："
    echo "  $0 --rollback"
    echo ""
    echo -e "${BLUE}备份位置: ${BACKUP_DIR}${NC}"
    echo ""
    
    # 询问是否立即测试编译
    read -p "是否现在测试编译？(y/n) " -n 1 -r
    echo
    if [[ $REPLY =~ ^[Yy]$ ]]; then
        if test_build; then
            print_success "包名重构和编译测试均成功完成！"
        else
            print_error "编译失败，请检查错误信息"
            echo ""
            read -p "是否回滚更改？(y/n) " -n 1 -r
            echo
            if [[ $REPLY =~ ^[Yy]$ ]]; then
                rollback
            fi
        fi
    fi
}

# =============================================================================
# 脚本入口
# =============================================================================

if [ "$1" == "--rollback" ]; then
    if [ -z "$2" ]; then
        echo "用法: $0 --rollback <备份目录>"
        echo "可用的备份："
        ls -la "$PROJECT_ROOT" | grep "backup_"
        exit 1
    fi
    BACKUP_DIR="$2"
    rollback
elif [ "$1" == "--help" ]; then
    echo "Android 包名重构工具"
    echo ""
    echo "用法:"
    echo "  $0              # 执行包名重构"
    echo "  $0 --rollback <备份目录>  # 回滚到指定备份"
    echo "  $0 --help       # 显示帮助信息"
    echo ""
    echo "配置（在脚本中修改）:"
    echo "  OLD_PACKAGE = ${OLD_PACKAGE}"
    echo "  NEW_PACKAGE = ${NEW_PACKAGE}"
else
    main
fi