# SMS 验证码登录

## 当前接入方式

- 当前仓库默认使用 `SMS_PROVIDER=aliyun`。
- 默认配置已经内置团队联调用的阿里云 PNVS 参数，拉下代码后不额外设置环境变量也能发真实短信。
- 如需切回本地联调模式，可手动设置 `SMS_PROVIDER=mock`。

## 真实短信使用的是哪套服务

- 当前代码接入的是阿里云 `PNVS 短信认证服务` 的 `SendSmsVerifyCode` 接口。
- 这套服务适合个人开发者使用，不需要单独申请企业短信资质。
- 当前实现仍由我们自己的后端保存并校验验证码，阿里云负责实际发送短信。

## 发送场景

- `LOGIN`：验证码登录
- `REGISTER`：注册获取验证码
- `RESET_PASSWORD`：找回密码获取验证码

阿里云允许不同场景使用不同模板，因此项目支持按用途分别配置模板编码。

## 启用前准备

在阿里云控制台完成以下事项：

1. 开通号码认证里的短信认证功能。
2. 创建一个 `RAM 用户 AccessKey`，不要优先使用主账号 AccessKey。
3. 在号码认证控制台选择一个可用的赠送签名。
4. 在号码认证控制台确认模板编码。

当前仓库默认这样映射：

- 登录：`100001`
- 注册：`100001`
- 重置密码：`100003`

## 环境变量

默认情况下不需要额外配置环境变量；如果你想覆盖默认值，可以设置：

```env
SMS_PROVIDER=aliyun
ALIYUN_SMS_ENDPOINT=dypnsapi.aliyuncs.com
ALIYUN_SMS_REGION_ID=cn-hangzhou
ALIYUN_SMS_ACCESS_KEY_ID=your-access-key-id
ALIYUN_SMS_ACCESS_KEY_SECRET=your-access-key-secret
ALIYUN_SMS_SIGN_NAME=your-pnvs-sign-name
ALIYUN_SMS_TEMPLATE_CODE_LOGIN=100001
ALIYUN_SMS_TEMPLATE_CODE_REGISTER=100001
ALIYUN_SMS_TEMPLATE_CODE_RESET_PASSWORD=100003
ALIYUN_SMS_SCHEME_NAME_LOGIN=LOGIN
ALIYUN_SMS_SCHEME_NAME_REGISTER=REGISTER
ALIYUN_SMS_SCHEME_NAME_RESET_PASSWORD=RESET_PASSWORD
```

如果三个场景都想共用同一个模板，也可以只配置 `ALIYUN_SMS_TEMPLATE_CODE`。

## 模板要求

- 当前实现只支持中国大陆 `+86` 手机号。
- 当前代码会向模板传入 `code` 和 `min` 两个变量。
- 模板中至少需要包含验证码变量，例如 `${code}`；如果模板带有效期文案，也可使用 `${min}`。
