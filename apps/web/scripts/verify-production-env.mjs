const failures = [];

if (process.env.VITE_LOCAL_MODE !== 'false') {
  failures.push('VITE_LOCAL_MODE must be exactly false; demo/local mode is forbidden for production deployments.');
}

for (const key of ['VITE_KEYCLOAK_URL', 'VITE_KEYCLOAK_REALM', 'VITE_KEYCLOAK_CLIENT_ID']) {
  if (!process.env[key]?.trim()) failures.push(key + ' must be set at build time.');
}


if (process.env.VITE_KEYCLOAK_REALM?.trim() !== 'education') {
  failures.push('VITE_KEYCLOAK_REALM must be exactly education.');
}
if (process.env.VITE_KEYCLOAK_CLIENT_ID?.trim() !== 'platform-web') {
  failures.push('VITE_KEYCLOAK_CLIENT_ID must be exactly platform-web.');
}

const issuer = process.env.VITE_KEYCLOAK_URL?.trim();
if (issuer) {
  try {
    const parsed = new URL(issuer);
    if (parsed.protocol !== 'https:') failures.push('VITE_KEYCLOAK_URL must use HTTPS.');
    if (parsed.hostname === 'localhost' || parsed.hostname === '127.0.0.1') {
      failures.push('VITE_KEYCLOAK_URL must not point to localhost.');
    }
    if (parsed.search || parsed.hash) failures.push('VITE_KEYCLOAK_URL must not contain a query or fragment.');
  } catch {
    failures.push('VITE_KEYCLOAK_URL must be a valid absolute URL.');
  }
}

if (process.env.VITE_BACKEND_URL?.trim()) {
  failures.push('VITE_BACKEND_URL must be unset for the same-origin production API service.');
}

if (failures.length) {
  console.error('Refusing to build UEP for production:');
  for (const failure of failures) console.error(' - ' + failure);
  process.exitCode = 1;
} else {
  console.log('production deployment environment is configured for real OIDC and same-origin API routing.');
}
