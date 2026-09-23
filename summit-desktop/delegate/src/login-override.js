/* SciVerse Summit — delegate login override.
 *
 * Injected by the Electron shell after navigation to the server's /login
 * page (did-finish-load hook). Strict no-op anywhere else. Swaps the
 * Thymeleaf body for this bundled design, mirroring the chair login UI
 * (light glass card, gradient brand text, icon inputs, gradient button)
 * with extra flair: glow ring, floating science glyphs, shine sweep.
 *
 * The replacement is a plain HTML <form method="post" action="/login">
 * with the exact contract the server expects (username + password, no
 * CSRF — see SecurityConfig), so auth, sessions and redirects behave
 * identically. Fully offline-safe: no external assets, inline styles only.
 * (Bootstrap Icons classes are used — the host page already loads them.)
 */
(function () {
  function isServerLogin() {
    if (!/^https?:$/.test(window.location.protocol)) return false;
    return window.location.pathname.replace(/\/+$/, '') === '/login';
  }

  function applyCustomLogin() {
    if (!isServerLogin()) return;
    if (document.documentElement.dataset.svLogin === '1') return;
    document.documentElement.dataset.svLogin = '1';

    var failed = new URLSearchParams(window.location.search).has('error');

    document.body.innerHTML =
      '<style>'
      + '.svl-bg{position:fixed;inset:0;overflow:hidden;'
      + 'background:linear-gradient(135deg,#f5f3ff 0%,#f8fafc 40%,#cffafe 100%);}'
      + '.svl-blob{position:absolute;border-radius:50%;filter:blur(80px);opacity:.35;'
      + 'animation:svl-drift 18s ease-in-out infinite alternate;}'
      + '.svl-blob.b1{width:500px;height:500px;top:-120px;left:-100px;'
      + 'background:radial-gradient(circle,rgba(124,58,237,.5),transparent 70%);}'
      + '.svl-blob.b2{width:450px;height:450px;bottom:-100px;right:-80px;'
      + 'background:radial-gradient(circle,rgba(16,185,129,.45),transparent 70%);'
      + 'animation-delay:-8s;}'
      + '@keyframes svl-drift{0%{transform:translate(0,0) scale(1);}'
      + '50%{transform:translate(40px,30px) scale(1.08);}'
      + '100%{transform:translate(-20px,-15px) scale(.95);}}'
      + '.svl-glyph{position:absolute;font-weight:800;color:rgba(124,58,237,.10);'
      + 'animation:svl-float 7s ease-in-out infinite alternate;user-select:none;}'
      + '@keyframes svl-float{from{transform:translateY(-12px) rotate(-6deg);}'
      + 'to{transform:translateY(12px) rotate(6deg);}}'
      + '.svl-wrap{min-height:100vh;display:flex;align-items:center;justify-content:center;'
      + 'padding:24px;box-sizing:border-box;position:relative;}'
      + '.svl-glow{position:relative;}'
      + '.svl-glow::before{content:"";position:absolute;inset:-3px;border-radius:27px;z-index:0;'
      + 'background:conic-gradient(from var(--svl-a,0deg),#06b6d4,#7c3aed,#ec4899,#06b6d4);'
      + 'filter:blur(14px);opacity:.45;animation:svl-spin 6s linear infinite;}'
      + '@keyframes svl-spin{to{--svl-a:360deg;}}'
      + '.svl-card{position:relative;z-index:1;width:100%;max-width:420px;'
      + 'background:#fff;border:1px solid rgba(100,116,139,.45);border-radius:24px;'
      + 'padding:44px 40px 36px;box-shadow:0 24px 60px rgba(15,23,42,.12),0 4px 16px rgba(15,23,42,.06);'
      + 'overflow:hidden;animation:svl-in .55s cubic-bezier(.2,.9,.25,1.15) both;}'
      + '@keyframes svl-in{from{opacity:0;transform:translateY(22px) scale(.97);}'
      + 'to{opacity:1;transform:none;}}'
      + '.svl-card::before{content:"";position:absolute;top:0;left:0;right:0;height:4px;'
      + 'background:linear-gradient(90deg,#06b6d4,#7c3aed);}'
      + '.svl-brand{display:flex;align-items:center;justify-content:center;gap:14px;margin-bottom:20px;}'
      + '.svl-brand img{width:60px;height:60px;object-fit:contain;border-radius:50%;'
      + 'box-shadow:0 6px 20px rgba(124,58,237,.35);}'
      + '.svl-brandtxt{display:flex;flex-direction:column;line-height:1.1;text-align:left;}'
      + '.svl-brandname{font-size:1.7rem;font-weight:800;'
      + 'background:linear-gradient(135deg,#06b6d4,#7c3aed);'
      + '-webkit-background-clip:text;background-clip:text;'
      + '-webkit-text-fill-color:transparent;color:transparent;}'
      + '.svl-brandsub{font-size:1.05rem;font-weight:700;letter-spacing:.22em;'
      + 'text-transform:uppercase;color:rgba(30,41,59,.6);}'
      + '.svl-title{font-size:1.75rem;font-weight:700;color:#1e293b;margin-bottom:6px;}'
      + '.svl-sub{font-size:.95rem;color:rgba(30,41,59,.55);margin-bottom:32px;}'
      + '.svl-label{display:block;font-size:.85rem;font-weight:600;color:rgba(30,41,59,.75);'
      + 'margin-bottom:6px;text-transform:uppercase;letter-spacing:.04em;}'
      + '.svl-field{display:flex;margin-bottom:16px;}'
      + '.svl-ico{display:flex;align-items:center;justify-content:center;width:50px;'
      + 'background:#f8fafc;border:1px solid rgba(100,116,139,.28);border-right:none;'
      + 'border-radius:12px 0 0 12px;color:rgba(30,41,59,.55);font-size:1rem;flex-shrink:0;}'
      + '.svl-field input{flex:1;min-width:0;height:50px;font-size:.95rem;color:#1e293b;'
      + 'background:#f8fafc;border:1px solid rgba(100,116,139,.28);border-left:none;'
      + 'border-radius:0 12px 12px 0;padding:0 14px;outline:none;transition:border-color .2s,box-shadow .2s,background .2s;}'
      + '.svl-field input::placeholder{color:rgba(30,41,59,.35);}'
      + '.svl-field input:focus{background:#fff;border-color:rgba(6,182,212,.5);'
      + 'box-shadow:0 0 0 3px rgba(6,182,212,.12);}'
      + '.svl-pass{position:relative;display:flex;margin-bottom:22px;}'
      + '.svl-pass input{padding-right:44px;}'
      + '.svl-eye{position:absolute;right:0;top:0;bottom:0;width:42px;border:none;background:none;'
      + 'color:rgba(30,41,59,.4);cursor:pointer;font-size:1rem;border-radius:0 12px 12px 0;}'
      + '.svl-eye:hover{color:#06b6d4;}'
      + '.svl-btn{position:relative;width:100%;padding:13px 20px;overflow:hidden;'
      + 'background:linear-gradient(135deg,rgba(6,182,212,.92),rgba(124,58,237,.92));'
      + 'border:1px solid rgba(255,255,255,.45);color:#fff;font-size:1.05rem;font-weight:700;'
      + 'border-radius:14px;letter-spacing:.03em;cursor:pointer;'
      + 'box-shadow:inset 0 1px 0 rgba(255,255,255,.35),0 6px 20px rgba(6,182,212,.35);'
      + 'transition:transform .15s,box-shadow .2s;}'
      + '.svl-btn:hover{transform:translateY(-1px);'
      + 'box-shadow:inset 0 1px 0 rgba(255,255,255,.45),0 8px 28px rgba(6,182,212,.45);}'
      + '.svl-btn::after{content:"";position:absolute;top:0;bottom:0;width:40%;left:-60%;'
      + 'background:linear-gradient(105deg,transparent,rgba(255,255,255,.45),transparent);'
      + 'transform:skewX(-20deg);animation:svl-shine 3.2s ease-in-out infinite;}'
      + '@keyframes svl-shine{0%,55%{left:-60%;}100%{left:160%;}}'
      + '.svl-err{background:rgba(239,68,68,.08);border:1px solid rgba(239,68,68,.3);'
      + 'color:#dc2626;border-radius:10px;padding:9px 12px;font-size:.85rem;font-weight:500;'
      + 'margin-bottom:14px;text-align:center;}'
      + '.svl-foot{font-size:.8rem;color:rgba(30,41,59,.5);margin-top:18px;text-align:center;}'
      + '</style>'
      + '<div class="svl-bg"><div class="svl-blob b1"></div><div class="svl-blob b2"></div>'
      + '<span class="svl-glyph" style="left:8%;top:14%;font-size:44px;">&#9883;</span>'
      + '<span class="svl-glyph" style="left:14%;top:68%;font-size:34px;animation-delay:-2s;">&pi;</span>'
      + '<span class="svl-glyph" style="left:86%;top:20%;font-size:38px;animation-delay:-4s;">&sum;</span>'
      + '<span class="svl-glyph" style="left:80%;top:72%;font-size:30px;animation-delay:-1s;">&lt;/&gt;</span>'
      + '<span class="svl-glyph" style="left:68%;top:8%;font-size:28px;animation-delay:-3s;">&Delta;</span>'
      + '<span class="svl-glyph" style="left:26%;top:86%;font-size:30px;animation-delay:-5s;">&infin;</span>'
      + '</div>'
      + '<div class="svl-wrap"><div class="svl-glow"><div class="svl-card">'
      + '<div class="svl-brand"><img src="/images/sciverse_summit_logo_final.png" alt="SciVerse Summit logo">'
      + '<div class="svl-brandtxt"><span class="svl-brandname">SciVerse Summit</span>'
      + '<span class="svl-brandsub">Delegate</span></div></div>'
      + '<div class="svl-title">Welcome Back</div>'
      + '<div class="svl-sub">Sign in to join your session.</div>'
      + (failed
          ? '<div class="svl-err">Wrong username or password. Try again.</div>'
          : '')
      // NOTE: keep method/action/names exactly as the server expects.
      + '<form method="post" action="/login">'
      + '<label class="svl-label" for="svlUser">Username</label>'
      + '<div class="svl-field"><span class="svl-ico"><i class="bi bi-person-fill"></i></span>'
      + '<input id="svlUser" type="text" name="username" placeholder="Username" required autocomplete="username"></div>'
      + '<label class="svl-label" for="svlPass">Password</label>'
      + '<div class="svl-pass"><div class="svl-field" style="flex:1;margin:0;">'
      + '<span class="svl-ico"><i class="bi bi-lock-fill"></i></span>'
      + '<input id="svlPass" type="password" name="password" placeholder="Password" required autocomplete="current-password"></div>'
      + '<button type="button" class="svl-eye" id="svlEye" aria-label="Show password"><i class="bi bi-eye-fill"></i></button></div>'
      + '<button type="submit" class="svl-btn">Sign In</button>'
      + '</form>'
      + '<div class="svl-foot">Ask your chair for credentials.</div>'
      + '</div></div></div>';

    // Password eye toggle (flair that needs JS).
    var eye = document.getElementById('svlEye');
    var pass = document.getElementById('svlPass');
    if (eye && pass) {
      eye.addEventListener('click', function () {
        var show = pass.type === 'password';
        pass.type = show ? 'text' : 'password';
        eye.innerHTML = '<i class="bi ' + (show ? 'bi-eye-slash-fill' : 'bi-eye-fill') + '"></i>';
      });
    }
    var user = document.getElementById('svlUser');
    if (user) user.focus();
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', applyCustomLogin);
  } else {
    applyCustomLogin();
  }
})();
