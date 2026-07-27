<img width="1920" height="956" alt="image" src="https://github.com/user-attachments/assets/029b64d9-3b17-45cc-8587-94d861275a21" />
<img width="1920" height="951" alt="image" src="https://github.com/user-attachments/assets/71a6484a-7f49-482e-8e9f-5f56dcf90664" />
<img width="1920" height="954" alt="image" src="https://github.com/user-attachments/assets/b106442d-b6c8-4643-9e8d-b09f84083191" />
<img width="1920" height="949" alt="image" src="https://github.com/user-attachments/assets/f760b5a8-fb01-4de9-b48a-9131cd6137d4" />
<img width="1920" height="951" alt="image" src="https://github.com/user-attachments/assets/7f127480-209c-4686-9195-efa22ea5623b" />
<img width="1920" height="955" alt="image" src="https://github.com/user-attachments/assets/b8e411f9-acbd-4323-93a5-29a16145baa2" />

# 🏋️ LiftCurve

A full-stack gym tracking platform built to help users manage workouts, track strength progression, monitor training volume, and analyze performance over time.

The project was developed as part of my software engineering portfolio, focusing on modern full-stack development practices, clean architecture, REST APIs, authentication, testing, and deployment.

## 🚀 Live Demo

**Application:** https://liftcurve.vercel.app/login

## ✨ Features

- Secure user authentication with JWT
- Workout and exercise management
- Training routine creation
- Progressive overload tracking
- 1RM calculation (Epley & Brzycki)
- Weekly training volume analysis
- Muscle group statistics
- Plateau detection
- Interactive dashboard
- Responsive interface

## 🛠️ Tech Stack

### Backend

- Java 21
- Quarkus
- PostgreSQL
- Flyway
- JWT Authentication
- Maven
- JUnit 5
- REST Assured

### Frontend

- React
- TypeScript
- Vite
- Tailwind CSS
- React Router
- Recharts
- Axios

### DevOps

- Docker
- Docker Compose
- GitHub Actions
- Render
- Vercel

## 🏗️ Architecture

The application follows a layered architecture separating presentation, business logic, and data access, making the codebase easier to maintain, test, and extend.

```
Frontend (React)
        │
        ▼
 REST API (Quarkus)
        │
        ▼
 Business Layer
        │
        ▼
 PostgreSQL Database
```

## 📸 Screenshots

> Screenshots and GIF demonstrations will be added soon.

## 🚀 Running Locally

### Requirements

- Java 21
- Node.js 20+
- Docker Desktop

### Clone the repository

```bash
git clone https://github.com/Raphael-Sinelli/liftcurve.git
```

### Start PostgreSQL

```bash
docker compose up -d postgres
```

### Backend

```bash
cd backend
./mvnw quarkus:dev
```

### Frontend

```bash
cd frontend
npm install
npm run dev
```

## 🧪 Tests

Backend

```bash
./mvnw test
```

Frontend

```bash
npm run test
```

## 👤 Demo Account

Use the following credentials to explore the application:

**Email**

```
demo@gymtracker.app
```

**Password**

```
DemoGymTracker2026!
```

## 🎯 Project Goals

This project was created to demonstrate practical experience with:

- Full-stack application development
- REST API design
- Authentication and authorization
- Database modeling
- Dockerized development
- Modern React development
- Clean architecture
- Automated testing
- CI/CD fundamentals

## 📈 Roadmap

- Mobile experience improvements
- Advanced training analytics
- Personal records timeline
- Exercise history filters
- Export training reports

## 👨‍💻 Author

**Raphael Oliveira Sinelli Mendonça**

- GitHub: https://github.com/Raphael-Sinelli
- LinkedIn: https://linkedin.com/in/raphael-sinelli-675310321
