import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient, HttpClientModule } from '@angular/common/http';
import { Router } from '@angular/router';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [CommonModule, FormsModule, HttpClientModule],
  templateUrl: './login.component.html',
  styleUrl: './login.component.scss'
})
export class LoginComponent {
  username = '';
  password = '';
  email = '';
  newPassword = '';
  showChangePassword = false;
  showRegister = false;
  showRecover = false;
  loading = false;

  constructor(private http: HttpClient, private router: Router) {}

  resetForm() {
    this.username = '';
    this.password = '';
    this.email = '';
    this.newPassword = '';
  }

  onLogin() {
    this.http.post<any>('http://localhost:8082/api/auth/login', {
      username: this.username,
      password: this.password
    }, { withCredentials: true }).subscribe({
      next: (user) => {
        if (user.firstLogin) {
          this.showChangePassword = true;
        } else {
          localStorage.setItem('user', JSON.stringify(user));
          this.router.navigate(['/documents']);
        }
      },
      error: () => alert('Usuario o contraseña incorrectos')
    });
  }

  onRegister() {
    if (!this.username || !this.password || !this.email) {
      alert('Por favor complete todos los campos');
      return;
    }

    this.http.post('http://localhost:8082/api/auth/register', {
      username: this.username,
      password: this.password,
      email: this.email
    }, { responseType: 'text', withCredentials: true }).subscribe({
      next: (res) => {
        alert(res);
        this.showRegister = false;
        this.resetForm();
      },
      error: (err) => {
        const errorMsg = typeof err.error === 'string' ? err.error : 'Error al registrar el usuario';
        alert(errorMsg);
      }
    });
  }

  onRecoverPassword() {
    if (!this.email) {
      alert('Por favor ingrese su correo electrónico');
      return;
    }

    this.loading = true;
    this.http.post('http://localhost:8082/api/auth/recover-password', {
      email: this.email
    }, { responseType: 'text', withCredentials: true }).subscribe({
      next: (res) => {
        alert(res);
        this.showRecover = false;
        this.loading = false;
        this.resetForm();
      },
      error: (err) => {
        this.loading = false;
        const errorMsg = typeof err.error === 'string' ? err.error : 'Error al recuperar contraseña';
        alert(errorMsg);
      }
    });
  }

  onChangePassword() {
    if (!this.password || !this.newPassword) {
      alert('Por favor complete todos los campos');
      return;
    }

    this.http.post('http://localhost:8082/api/auth/change-password', {
      username: this.username,
      oldPassword: this.password,
      newPassword: this.newPassword
    }, { responseType: 'text', withCredentials: true }).subscribe({
      next: () => {
        alert('Contraseña actualizada. Por favor inicie sesión nuevamente.');
        this.showChangePassword = false;
        this.resetForm();
      },
      error: (err) => {
        const errorMsg = typeof err.error === 'string' ? err.error : 'Error al actualizar la contraseña';
        alert(errorMsg);
      }
    });
  }
}
