# Free Tier UEP Deployment Guide

This guide provides a complete step-by-step deployment of the Universal Education Platform (UEP) using **100% free tier services** for testing.

---

## 📋 Prerequisites

### Required Tools (Install these first)
```bash
# Node.js (for build tools)
nvm use 22.22.0

# Git (for version control)
git --version

# wget/curl (for downloads)
which wget || which curl

# Docker (optional, for testing locally)
which docker
```

### Cloudflare Access
- Cloudflare account with wrangler installed
- Email: `ssjagtap2016@gmail.com` (from previous deployment)
- wrangler version: `4.156.0`

### Railway Access (Free Backend Deployment)
- Railway account (free tier available)
- GitHub account (for Git integration)

---

## 🚀 Phase 1: Frontend Deployment (Completed)

✅ **Status**: DEPLOYED AND RUNNING

**Current Deployment**:
- URL: `https://uep-frontend-testing.pages.dev`
- Project: `uep-frontend-testing`
- Files: 3 static assets uploaded
- Environment: `VITE_LOCAL_MODE=true` (demo mode)

**Access Immediately**:
```
https://uep-frontend-testing.pages.dev
```

**Login Credentials (all accounts use same password)**:
- `asha.student@demo.uep` / `demo`
- `rahul.teacher@demo.uep` / `demo`
- `priya.client@demo.uep` / `demo`
- `admin.platform@demo.uep` / `demo`
- Plus 14 more demo accounts...
```

---

## 🖥️ Phase 2: Backend Deployment (Free Tier)

### Option 1: Railway (Recommended for Java)

#### Step 1: Create Railway Account
```bash
# Visit: https://railway.com
# Sign up with GitHub/Google
# Complete email verification
```

#### Step 2: Deploy from GitHub
```bash
# Fork this repository (or use your fork)
# Go to Railway dashboard
# Click "New Project" → "Deploy from GitHub repository"
# Select your UEP repository
# Choose branch (usually "main" or "develop")
```

#### Step 3: Configure Build Settings
In Railway dashboard:

**Build Command**:
```bash
C:\apache-maven\apache-maven-3.10.0\bin\mvn clean package -DskipTests
```

**Start Command**:
```bash
C:\apache-maven\apache-maven-3.10.0\bin\mvn spring-boot:run
```

#### Step 4: Environment Variables
Add in Railway dashboard "Variables" tab:

```bash
SPRING_PROFILES_ACTIVE=production
VITE_LOCAL_MODE=false
DATABASE_URL=jdbc:postgresql://your-railway-db:5432/education_platform
DATABASE_USERNAME=education
DATABASE_PASSWORD=your-db-password
```

**Note**: Railway provides a PostgreSQL database automatically with the free tier.

#### Step 5: Deploy
```bash
# Railway will build and deploy automatically
# Wait 5-10 minutes for first deployment
```

**Result**:
- URL: `https://your-project-railway.app`
- Database: PostgreSQL (free tier)
- Build logs: Available in Railway dashboard

---

### Option 2: AWS Elastic Beanstalk (Free Tier)

#### Step 1: Create AWS Account
```bash
# Visit: https://console.aws.amazon.com
# Create free tier account if needed
```

#### Step 2: Deploy Java Application
```bash
# Use AWS CLI or AWS Management Console
aws elasticbeanstalk create-application-version \
  --application-name uep-backend \
  --version-label v1 \
  --source-bundle S3Bucket=my-bucket,S3Key=uep-1.0.0.jar

aws elasticbeanstalk create-environment \
  --application-name uep-backend \
  --environment-name uep-prod \
  --solution-stack-name "64bit Amazon Linux 2 v5.9.0 running OpenJDK 21" \
  --version-label v1 \
  --environment-type Single
```

#### Step 3: Configure Environment Variables
```bash
aws elasticbeanstalk update-environment \
  --environment-name uep-prod \
  --option-settings Namespace=aws:elasticbeanstalk:application:environment \
    Key=SPRING_PROFILES_ACTIVE,Value=production \
    Key=VITE_LOCAL_MODE,Value=false \
    Key=DATABASE_URL,Value=jdbc:postgresql://your-rds-endpoint:5432/education_platform
```

---

## 🔗 Phase 3: Connect Frontend to Backend

### Step 1: Update Cloudflare Pages Environment
```bash
wrangler pages secrets put VITE_BACKEND_URL "https://your-railway-project.railway.app"
wrangler pages secrets put VITE_LOCAL_MODE "false"
wrangler pages secrets put VITE_KEYCLOAK_URL "https://auth.your-keycloak-domain.com"
```

### Step 2: Redeploy Frontend
```bash
wrangler pages deploy --project-name uep-frontend-testing C:\Users\siddh\Downloads\ABC\UEP\apps\web\dist
```

### Step 3: Test the Connection
```bash
# The frontend should now call:
# https://your-railway-project.railway.app/api/v1/...
# instead of local backend
```

---

## 🧪 Phase 4: Testing Strategy

### Phase 4A: Local Development (Quick Testing)
```bash
# 1. Build frontend locally
npm run build

# 2. Start backend locally (if you have Java/maven)
cd apps/platform-server
mvn spring-boot:run

# 3. Test locally
http://localhost:5173
```

### Phase 4B: Railway Testing (Integration Testing)
```bash
# 1. Access deployed backend
https://your-railway-project.railway.app/health

# 2. Access deployed frontend
https://uep-frontend-testing.pages.dev

# 3. Test API endpoints
curl https://your-railway-project.railway.app/api/v1/platform/version
```

### Phase 4C: Production Testing
```bash
# Use Postman or browser to test all endpoints
# Test with real users and data
# Test with Keycloak integration
# Test with all 18 demo accounts
```

---

## 📊 Current Progress

| Component | Status | Access | Notes |
|-----------|--------|--------|-------|
| **Frontend** | ✅ Deployed | `https://uep-frontend-testing.pages.dev` | Local mode, demo data |
| **Backend** | ⏳ Ready for Railway deployment | N/A | Java/Spring Boot |
| **Database** | ⏳ Railway auto-provided | N/A | PostgreSQL free tier |
| **Integration** | ⏳ Pending | N/A | Needs frontend-backend connection |

---

## 🎯 Immediate Action Items

### For You (To Complete Deployment):

1. **✅ Done**: Frontend deployed to Cloudflare Pages
2. **⏳ Next**: Deploy backend to Railway
   ```bash
   # Go to: https://railway.com
   # Click "New Project" → "Deploy from GitHub"
   # Select your UEP repository
   # Configure build/start commands
   ```

3. **⏳ Next**: Configure Railway environment variables
4. **⏳ Next**: Update Cloudflare Pages environment
5. **⏳ Next**: Test full integration

### During Backend Deployment:
- **Build Time**: 5-10 minutes
- **Wait Time**: 2-3 minutes after deployment
- **URL**: Provided in Railway dashboard
- **Database**: Automatic PostgreSQL (free tier)

---

## 💡 Tips for Free Tier Success

### Railway Tips:
- Use `free` plan for testing
- Restart service after environment changes
- Check build logs for compilation errors
- Use PostgreSQL from free tier (100MB storage)

### Cloudflare Tips:
- Use Pages for static frontend
- Use environment variables for configuration
- Test with different domains
- Use custom domains for production

### Cost Monitoring:
- Railway free tier: ~$10/month limit
- Cloudflare Pages free tier: $500/month limit
- AWS free tier: ~$100/month limit

---

## 🚀 Production Readiness Checklist

### ✅ Already Done:
- [x] Frontend deployed (Cloudflare Pages)
- [x] Static assets built and optimized
- [x] Local demo mode configured
- [x] Environment variables documented

### ⏳ To Do:
- [ ] Backend deployed (Railway)
- [ ] Database configured
- [ ] Environment variables set
- [ ] Integration tested
- [ ] Custom domain configured
- [ ] SSL certificates set up
- [ ] Monitoring and logging setup

---

## 🔧 Troubleshooting Common Issues

### Build Errors:
```bash
# Railway build log error
# Check: Railway dashboard → "Logs"
# Common issues:
# - Java version mismatch
# - Missing dependencies
# - Database connection issues
```

### Connection Issues:
```bash
# If frontend can't reach backend:
# 1. Check backend health endpoint
# 2. Verify environment variables
# 3. Check network allow rules
# 4. Test with curl
```

---

## 📞 Support

If you need help during deployment:
1. **Railway Discord**: https://discord.railway.com
2. **GitHub Issues**: Report deployment problems
3. **Stack Overflow**: Questions with `[railway]` tag
4. **Cloudflare Discord**: For Pages deployment issues

---

## 🎉 Next Steps

1. **Complete backend deployment** (5-10 minutes)
2. **Configure environment variables** (2 minutes)
3. **Test integration** (10-15 minutes)
4. **Go live** with custom domain (5 minutes)

**Total time to production-ready UEP**: **15-25 minutes**

---

**Status Update**: Frontend ✅ | Backend ⏳ | Database ⏳ | Integration ⏳

Your UEP project is ready for deployment! Just complete the Railway setup to have a fully functional UEP alongside your portfolio.