// SPDX-License-Identifier: LGPL-3.0
//! nativehook —— 用 Rust 实现 libxposed 原生 hook 的最小参考模板。
//!
//! 构建（需 `cargo-ndk`，产物即为 `libnativehook.so`）：
//! ```text
//! cargo install cargo-ndk
//! rustup target add aarch64-linux-android armv7-linux-androideabi x86_64-linux-android
//! cargo ndk -t arm64-v8a -t armeabi-v7a -t x86_64 \
//!     -o ../../../jniLibs build --release
//! ```
//! 然后把 `libnativehook.so` 拷进 `hook/src/main/jniLibs/<abi>/`，
//! 并写入 `hook/src/main/resources/META-INF/xposed/native_init.list`。
//!
//! 注意：请勿在 `native_init` 内做阻塞 / Binder 重活；它是进程启动早期被调用的。

#![allow(non_snake_case)]

use core::ffi::{c_char, c_int, c_void};
use core::sync::atomic::{AtomicPtr, Ordering};
use std::ffi::{CStr, CString};

/// 框架提供的内联 hook 原语。
pub type HookFunType =
    unsafe extern "C" fn(func: *mut c_void, replace: *mut c_void, backup: *mut *mut c_void) -> c_int;
/// 框架提供的取消 hook 原语。
pub type UnhookFunType = unsafe extern "C" fn(func: *mut c_void) -> c_int;
/// 每个 so 载入时的回调。
pub type NativeOnModuleLoaded = unsafe extern "C" fn(name: *const c_char, handle: *mut c_void);

/// 框架传入的结构体（只读）。
#[repr(C)]
pub struct NativeAPIEntries {
    pub version: u32,
    pub hook_func: Option<HookFunType>,
    pub unhook_func: Option<UnhookFunType>,
}

static HOOK: AtomicPtr<c_void> = AtomicPtr::new(core::ptr::null_mut());
static UNHOOK: AtomicPtr<c_void> = AtomicPtr::new(core::ptr::null_mut());

/// 已 hook 目标函数的原始入口（用于幂等与调用原函数）。
static ORIGINAL: AtomicPtr<c_void> = AtomicPtr::new(core::ptr::null_mut());

/// 模板目标：改成你要 hook 的 Rust 库与符号（优先 C ABI 导出，如 `frb_*` / `wire_*`）。
const TARGET_LIB: &str = "libresources_frb.so";
const TARGET_SYMBOL: &str = "frb_verify_license";

extern "C" {
    fn __android_log_print(prio: c_int, tag: *const c_char, fmt: *const c_char, ...) -> c_int;

    fn dlsym(handle: *mut c_void, symbol: *const c_char) -> *mut c_void;
}

fn log(msg: &str) {
    let tag = CString::new("NativeHook").unwrap_or_default();
    let fmt = CString::new("%s").unwrap_or_default();
    let text = CString::new(msg).unwrap_or_default();
    unsafe {
        __android_log_print(4, tag.as_ptr(), fmt.as_ptr(), text.as_ptr());
    }
}

/// 安装 hook：成功返回可调用原函数的 trampoline。
pub fn install_hook(target: *mut c_void, replace: *mut c_void) -> Result<*mut c_void, c_int> {
    let raw = HOOK.load(Ordering::Acquire);
    if raw.is_null() {
        return Err(-1);
    }
    // SAFETY: 指针来自框架注入的 HookFunType。
    let hook: HookFunType = unsafe { core::mem::transmute(raw) };
    let mut backup: *mut c_void = core::ptr::null_mut();
    let ret = unsafe { hook(target, replace, &mut backup) };
    if ret == 0 {
        Ok(backup)
    } else {
        Err(ret)
    }
}

/// 取消 hook。
pub fn uninstall_hook(target: *mut c_void) -> c_int {
    let raw = UNHOOK.load(Ordering::Acquire);
    if raw.is_null() {
        return -1;
    }
    // SAFETY: 指针来自框架注入的 UnhookFunType。
    let unhook: UnhookFunType = unsafe { core::mem::transmute(raw) };
    unsafe { unhook(target) }
}

/// 从已加载库中解析符号。
pub fn lookup(handle: *mut c_void, symbol: &str) -> *mut c_void {
    let Ok(name) = CString::new(symbol) else {
        return core::ptr::null_mut();
    };
    unsafe { dlsym(handle, name.as_ptr()) }
}

/// 目标函数的替换实现；需要调用原函数时使用 [ORIGINAL] 中保存的地址。
unsafe extern "C" fn fake_target(code: c_int) -> c_int {
    log(&format!("hooked {TARGET_SYMBOL}(code={code}) -> return 0"));
    0
}

unsafe extern "C" fn on_module_loaded(name: *const c_char, handle: *mut c_void) {
    if name.is_null() || handle.is_null() {
        return;
    }
    let lib_name = unsafe { CStr::from_ptr(name) }.to_string_lossy();
    log(&format!("library loaded: {lib_name}"));

    if !lib_name.ends_with(TARGET_LIB) {
        return;
    }
    // 幂等：重复加载时不要重复 hook。
    if !ORIGINAL.load(Ordering::Acquire).is_null() {
        return;
    }
    let target = lookup(handle, TARGET_SYMBOL);
    if target.is_null() {
        log(&format!("symbol not found: {TARGET_SYMBOL} in {lib_name}"));
        return;
    }
    match install_hook(target, fake_target as *mut c_void) {
        Ok(backup) => {
            ORIGINAL.store(backup, Ordering::Release);
            log(&format!("hooked {TARGET_SYMBOL} in {lib_name}"));
        }
        Err(code) => log(&format!("hook failed: {TARGET_SYMBOL}, code={code}")),
    }
}

/// 框架入口：与 libxposed 原生入口 `native_init` 一一对应（C ABI）。
#[no_mangle]
pub extern "C" fn native_init(entries: *const NativeAPIEntries) -> Option<NativeOnModuleLoaded> {
    if entries.is_null() {
        return None;
    }
    let entries = unsafe { &*entries };
    if let Some(hook) = entries.hook_func {
        HOOK.store(hook as *mut c_void, Ordering::Release);
    }
    if let Some(unhook) = entries.unhook_func {
        UNHOOK.store(unhook as *mut c_void, Ordering::Release);
    }
    log(&format!("native_init called, api version={}", entries.version));
    Some(on_module_loaded)
}
