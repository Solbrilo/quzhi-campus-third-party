<?php
require_once __DIR__ . '/_lib/projects.php';

$project = find_project(load_projects(), 'qzxy-hotwater');
$pageTitle = '沐芳：轻松用水，清楚记账 - Nebularive';
$pageDesc = '沐芳是趣智校园第三方客户端，提供 Android 与 HarmonyOS NEXT 原生应用。了解 Android 4.0.0 的校园账单、半屏支付宝充值、七套主题、加高的 2×1 小组件和李白诗句欢迎页。';
$activeNav = 'projects';
include __DIR__ . '/_partials/header.php';
?>
<link rel="stylesheet" href="/static/css/qzxy-project.css?v=20261114-mufang">

<main class="hero scroll-stage project-hero qzxy-hero">
    <div class="hero-inner qzxy-hero-inner scroll-item" style="--delay: 80ms">
        <div class="qzxy-hero-copy">
            <p class="eyebrow">Project / QZXY Hotwater</p>
            <h1>沐芳</h1>
            <p class="lead">打开应用，看余额，开关热水。把账单、充值和校园消息放在顺手的位置，让每天的用水更简单。</p>
            <?php render_project_detail_actions($project, '/project-qzxy-hotwater.php'); ?>
            <p class="muted qzxy-release-note">Android 源码版本 v4.0.0 · 安装包版本以 Gitee Releases 为准</p>
        </div>
        <figure class="qzxy-app-mark" aria-label="沐芳应用图标">
            <img src="/static/images/qzxy-hotwater-app-icon.webp?v=20261114-mufang" alt="沐芳应用图标" width="1024" height="1024" fetchpriority="high">
        </figure>
    </div>
</main>

<section class="section scroll-stage">
    <div class="container">
        <div class="stats qzxy-release-facts scroll-item" style="--delay: 100ms">
            <div class="stat"><span class="stat-num">Android 4.0.0</span><span class="stat-label">当前源码版本</span></div>
            <div class="stat"><span class="stat-num">7 套主题</span><span class="stat-label">开发版 · 深浅色自由切换</span></div>
            <div class="stat"><span class="stat-num">多尺寸</span><span class="stat-label">桌面快捷控制组件</span></div>
            <div class="stat"><span class="stat-num">双端原生</span><span class="stat-label">Android / HarmonyOS NEXT</span></div>
        </div>
    </div>
</section>

<section class="section scroll-stage" id="screenshots">
    <div class="container">
        <div class="section-head scroll-item" style="--delay: 100ms">
            <p class="eyebrow">A Closer Look</p>
            <h2>先看一眼，日常会用到的界面</h2>
            <p class="muted">Android 4.0.0 界面的模拟器截图，使用演示设备、余额和账单。充值套餐仅作展示，实际金额由学校提供。左右滑动查看更多，点击图片可查看大图。</p>
        </div>
        <div class="qzxy-gallery scroll-item" tabindex="0" role="region" aria-label="Android 应用截图，可横向滚动" style="--delay: 160ms">
            <?php
            $screenshots = [
                ['android-home-tide', '用水 · 潮汐', '连接状态、可用余额与一键开关。'],
                ['android-bills', '账单，一眼看清', '按月或按年筛选，汇总费用与用水量。'],
                ['android-recharge', '半屏充值', '选好金额，直接交给支付宝完成付款。'],
                ['android-home-ember', '余晖 · 浅色', '温暖陶橙，柔和的轮廓与日晕。'],
                ['android-home-mono', '墨序 · 深色', '克制石墨色，清楚利落的线条。'],
                ['android-open-source', '开源支持', '记录参考项目、用途与许可。'],
            ];
            foreach ($screenshots as [$file, $title, $caption]):
                $imageUrl = '/static/images/qzxy/screenshots-20261106/' . $file . '.png'
                    . ($file === 'android-open-source' ? '?v=20261114-mufang' : '');
            ?>
            <figure class="qzxy-shot">
                <a href="<?php echo htmlspecialchars($imageUrl, ENT_QUOTES, 'UTF-8'); ?>" target="_blank" rel="noopener" aria-label="<?php echo htmlspecialchars('查看大图：' . $title, ENT_QUOTES, 'UTF-8'); ?>">
                    <img src="<?php echo htmlspecialchars($imageUrl, ENT_QUOTES, 'UTF-8'); ?>" alt="<?php echo htmlspecialchars($title . '，演示数据', ENT_QUOTES, 'UTF-8'); ?>" width="720" height="1600" loading="lazy" decoding="async">
                </a>
                <figcaption><h3><?php echo htmlspecialchars($title, ENT_QUOTES, 'UTF-8'); ?></h3><p class="muted"><?php echo htmlspecialchars($caption, ENT_QUOTES, 'UTF-8'); ?></p></figcaption>
            </figure>
            <?php endforeach; ?>
        </div>
    </div>
</section>

<section class="section scroll-stage">
    <div class="container">
        <div class="section-head scroll-item" style="--delay: 100ms">
            <p class="eyebrow">Everyday Essentials</p>
            <h2>少些步骤，多些顺手</h2>
            <p class="muted">以下为 Android 4.0.0 当前功能。安装包所含功能以 Gitee Releases 对应发行说明为准。</p>
        </div>
        <div class="project-grid qzxy-feature-grid">
            <article class="project scroll-item"><p class="project-kicker">01 / Water</p><h3>状态清楚，开关直接</h3><p>绿色“已连接”、蓝色“正在使用”、黄色“设备占用中”、红色“离线”，一眼看清设备状态。附近设备、扫码和手动输入都能用于绑定。</p></article>
            <article class="project scroll-item"><p class="project-kicker">02 / Bills</p><h3>账单按你的时间查看</h3><p>默认查看本月，也能切换月份和年度。费用与用水量集中汇总，未同步的数据明确提示；登录失效时直接提供登录入口。</p></article>
            <article class="project scroll-item"><p class="project-kicker">03 / Recharge</p><h3>选金额，打开支付宝</h3><p>充值面板从底部升起，展示学校提供的可选金额。确认后拉起支付宝，返回时重新查询余额，并防止连续点击重复下单。</p></article>
            <article class="project scroll-item"><p class="project-kicker">04 / Messages</p><h3>校园消息，不遗漏</h3><p>未读红点、消息列表、一键已读与原生详情。进入详情后，标题和发布时间各保留一份，阅读更清楚。</p></article>
            <article class="project scroll-item"><p class="project-kicker">05 / Appearance</p><h3>简洁，也有自己的风格</h3><p>澄空、青屿、暮紫、暖砂、潮汐、余晖和墨序，配合深浅色模式与可调玻璃模糊。自定义背景、主题色、圆角与开关动画，切换时平滑过渡。</p></article>
            <article class="project scroll-item"><p class="project-kicker">06 / Quick Access</p><h3>桌面上，也能顺手开关</h3><p>高度更舒展的 2×1 滑动开关、2×2 余额控制和多种大尺寸状态组件，配合系统磁贴与用水通知。震动、开关水提醒和实况通知可分别设置。</p></article>
        </div>
    </div>
</section>

<section class="section scroll-stage">
    <div class="container split">
        <div class="panel scroll-item">
            <h2>从登录，到开始用水</h2>
            <ol class="qzxy-steps">
                <li><strong>登录自己的账号</strong><span>Android 支持短信验证码或密码登录，密码和验证码不会写入本地配置。</span></li>
                <li><strong>绑定学校的热水器</strong><span>选择附近设备，或扫描设备二维码；相机不可用时可以手动填写设备码。</span></li>
                <li><strong>回到首页，开始用水</strong><span>查看余额和连接状态，开水、关水，再到“账单”查看消费。登录和绑定也可以稍后完成。</span></li>
            </ol>
        </div>
        <div class="panel panel-highlight scroll-item">
            <h3>沐芳莫弹冠，浴兰莫振衣。</h3>
            <p class="muted">——李白《沐浴子》</p>
            <p>初次打开或更新后，两句宋体诗从右到左相隔 0.6 秒舒缓入场，“沐芳”两字以主题色突出。诗句显完停留约 0.6 秒，再浮现左侧竖排署名，配合轻柔震动。</p>
            <ul class="focus-list">
                <li>更新弹窗可忽略当前版本，有更新的版本时再提醒</li>
                <li>手动检查更新仍可查看之前忽略的版本</li>
                <li>主题、深浅色、玻璃强度、震动与通知集中在设置页</li>
                <li>支持配置备份、问题反馈与开源项目入口</li>
            </ul>
        </div>
    </div>
</section>

<section class="section scroll-stage">
    <div class="container">
        <div class="section-head scroll-item"><p class="eyebrow">Native Clients</p><h2>两端原生，分别打磨</h2></div>
        <div class="project-grid qzxy-platform-grid">
            <article class="project scroll-item"><p class="project-kicker">Android</p><h3>适合每天打开的轻巧工具</h3><p>Android 8.0 及以上可用，当前源码版本为 4.0.0 (31)。半屏充值、七套主题、自定义外观和多尺寸小组件围绕日常用水打磨；安装包以 Gitee Releases 为准。</p><div class="meta"><span>Kotlin / Compose</span><span>加密配置</span><span>桌面小组件</span></div></article>
            <article class="project scroll-item"><p class="project-kicker">HarmonyOS NEXT</p><h3>保留鸿蒙的原生体验</h3><p>提供账号登录、Scan Kit 扫码、热水控制、历史记录、服务卡片与配置备份。源码版本为 2.3.1，需按仓库说明自行签名构建；Android 新功能尚未全部同步。</p><div class="meta"><span>ArkTS / ArkUI</span><span>Scan Kit</span><span>服务卡片</span></div></article>
        </div>
    </div>
</section>

<section class="section scroll-stage">
    <div class="container">
        <div class="section-head scroll-item"><p class="eyebrow">Built With Open Source</p><h2>开源支持，有迹可循</h2><p class="muted">应用设置中新增开源支持页，也在仓库中保留相应许可与归属说明。</p></div>
        <div class="project-grid qzxy-feature-grid">
            <article class="project scroll-item"><h3><a href="https://github.com/yehu-imei/linyu" target="_blank" rel="noopener">LinYu ↗</a></h3><p>短信登录、附近设备发现与账单接口的实现参考。</p><div class="meta"><span>MIT</span></div></article>
            <article class="project scroll-item"><h3><a href="https://github.com/Ranxian/muguang" target="_blank" rel="noopener">muguang · 沐光 ↗</a></h3><p>充值套餐查询、订单签名及支付宝支付流程的实现参考。</p><div class="meta"><span>MIT</span></div></article>
            <article class="project scroll-item"><h3><a href="https://github.com/Kyant0/AndroidLiquidGlass" target="_blank" rel="noopener">AndroidLiquidGlass ↗</a></h3><p>Backdrop 提供背景采样、模糊、折射与高光，构成液态玻璃材质。</p><div class="meta"><span>Apache-2.0</span></div></article>
        </div>
    </div>
</section>

<section class="section scroll-stage" id="download">
    <div class="container split">
        <div class="panel scroll-item">
            <p class="eyebrow">Get Started</p><h2>下载，或从源码开始</h2>
            <p>Android 安装包通过 Gitee Releases 发布。当前源码版本为 4.0.0，下载后请查看对应发行版的更新说明。</p>
            <p>开发者也可在仓库中找到 Android、HarmonyOS 构建文档，以及保留的 Python 桌面兼容工具。</p>
            <div class="cta-row"><a class="cta" href="https://gitee.com/Solbrilo/quzhi-campus-third-party/releases" target="_blank" rel="noopener">下载 Android 正式版</a><a class="ghost" href="https://gitee.com/Solbrilo/quzhi-campus-third-party" target="_blank" rel="noopener">查看源码</a></div>
        </div>
        <div class="panel panel-highlight scroll-item">
            <h3>使用前，了解这些边界</h3>
            <p>这是独立的第三方客户端，与学校、设备运营方及趣智校园官方无隶属关系。可用设备、充值金额与支付通道取决于学校，余额与结算以服务器记录为准。</p>
            <p>Android 匿名活跃安装统计始终开启，不含账号、手机号或硬件标识；配置备份包含运行凭据，请仅保存在自己的设备上。</p>
            <p class="muted">截图为离线演示，不代表真实充值到账已经验收。实际设备控制与支付仍需在对应学校环境中确认。</p>
        </div>
    </div>
</section>

<?php include __DIR__ . '/_partials/footer.php'; ?>
