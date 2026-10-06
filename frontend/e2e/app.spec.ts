import { test, expect } from '@playwright/test';

test.describe('EasyCloudPan E2E Tests', () => {

  test('homepage loads successfully', async ({ page }) => {
    await page.goto('/');

    await expect(page).toHaveTitle(/EasyCloudPan|网盘|Easypan/);

    await expect(page.locator('body')).toBeVisible();
  });

  test('login page is accessible', async ({ page }) => {
    await page.goto('/login');

    await expect(page.locator('input[type="text"]').first()).toBeVisible();
    await expect(page.locator('input[type="password"]').first()).toBeVisible();
  });

  test('API health check returns valid response', async ({ request }) => {
    const response = await request.get('/api/actuator/health');

    expect(response.ok()).toBeTruthy();

    const health = await response.json();

    expect(health.components.db.status).toBe('UP');
    expect(health.components.redis.status).toBe('UP');
  });

  test('getUseSpace API returns data', async ({ request }) => {
    const response = await request.get('/api/getUseSpace');

    expect(response.ok()).toBeTruthy();

    const data = await response.json();

    expect(data.code).toBe(901);

  });

  test('file list requires authentication', async ({ request }) => {
    const response = await request.post('/api/file/loadDataList', {
      headers: {
        'Content-Type': 'application/x-www-form-urlencoded',
      },
    });

    const data = await response.json();

    expect(data.code).toBe(901);
    expect(data.info).toContain('登录');
  });

  test('share list requires authentication', async ({ request }) => {
    const response = await request.post('/api/share/loadShareList', {
      headers: {
        'Content-Type': 'application/x-www-form-urlencoded',
      },
    });

    const data = await response.json();

    expect(data.code).toBe(901);
  });

  test('recycle list requires authentication', async ({ request }) => {
    const response = await request.post('/api/recycle/loadRecycleList', {
      headers: {
        'Content-Type': 'application/x-www-form-urlencoded',
      },
    });

    const data = await response.json();

    expect(data.code).toBe(901);
  });

  test('admin endpoints require admin role', async ({ request }) => {
    const response = await request.post('/api/admin/loadUserList', {
      headers: {
        'Content-Type': 'application/x-www-form-urlencoded',
      },
    });

    const data = await response.json();

    expect(data.code).toBe(901);
  });
});
