# 🏋️ LiftCurve

A modern full-stack gym tracking platform built to help users manage workouts, track strength progression, monitor training volume, and analyze long-term performance.

Built with **Java (Quarkus)**, **React**, **TypeScript**, **PostgreSQL**, and **Docker**, LiftCurve demonstrates modern software engineering practices, including REST APIs, JWT authentication, clean architecture, automated testing, CI/CD, and cloud deployment.

## 🚀 Live Demo

🌐 **Application:** https://liftcurve.vercel.app/login

---

## ✨ Features

- Secure authentication with JWT
- Workout and exercise management
- Custom training routines
- Progressive overload tracking
- One Rep Max (1RM) estimation
- Weekly training volume analysis
- Muscle group statistics
- Plateau detection
- Interactive analytics dashboard
- Responsive user interface

---

## 📸 Screenshots

### Dashboard

![Dashboard](docs/images/dashboard.png)

### Workout Management

![Workout](docs/images/workout.png)

### Exercise Library

![Exercises](docs/images/exercises.png)

### Analytics

![Analytics](docs/images/analytics.png)

### User Profile

![Profile](docs/images/profile.png)

---

## 🛠 Tech Stack

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
- Axios
- Recharts

### DevOps

- Docker
- Docker Compose
- GitHub Actions
- Render
- Vercel

---

## 🏗 Architecture

```
React
   │
REST API
   │
Quarkus
   │
PostgreSQL
```

The application follows a layered architecture, separating presentation, business logic, and persistence layers to improve maintainability, scalability, and testability.

---

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

---

## 🧪 Running Tests

Backend

```bash
./mvnw test
```

Frontend

```bash
npm run test
```

---

## 👤 Demo Account

Use the following account to explore the application.

**Email**

```
demo@gymtracker.app
```

**Password**

```
DemoGymTracker2026!
```

---

## 🎯 Project Goals

This project was developed to demonstrate practical experience with:

- Full-stack application development
- REST API design
- JWT authentication
- Database modeling
- Dockerized environments
- Modern React development
- Clean Architecture
- Automated testing
- CI/CD pipelines

---

## 📈 Roadmap

- Mobile experience improvements
- Advanced workout analytics
- Personal records timeline
- Exercise history filters
- Export training reports

---

## 👨‍💻 Author

**Raphael Oliveira Sinelli Mendonça**

GitHub: https://github.com/Raphael-Sinelli

LinkedIn: https://linkedin.com/in/raphael-sinelli-675310321
