// Paquete que contiene el controlador de autenticación
package com.example.Backend.Controller;

// Importaciones de Spring Framework necesarias para el controlador
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;

// Importaciones de DTOs y servicios de la aplicación
import com.example.Backend.DTO.JwtResponseDto;
import com.example.Backend.DTO.LoginDto;
import com.example.Backend.DTO.RegisterDto;
import com.example.Backend.DTO.UserDTO;
import com.example.Backend.DTO.AlertDTO;
import com.example.Backend.DTO.UserConfirmationDTO;
import com.example.Backend.Security.JwtGenerator;
import com.example.Backend.Service.RolService;
import com.example.Backend.Service.UserService;

import java.util.List;

// Controlador REST que maneja la autenticación y gestión de usuarios
@RestController
@RequestMapping("/api")
public class AuthController {

    // Inyección del servicio de usuarios
    @Autowired
    private UserService userService;
    
    // Inyección del servicio de roles
    @Autowired
    private RolService rolService;
    
    // Inyección del generador de tokens JWT
    @Autowired
    private JwtGenerator jwtGenerator;

    // Endpoint para autenticar usuarios y generar token JWT
    @PostMapping("/login")
    public ResponseEntity<JwtResponseDto> login(@RequestBody LoginDto loginDto) {
        return ResponseEntity.ok(userService.login(loginDto));
    }
    
    // Endpoint para registrar nuevos clientes con confirmación
    @PostMapping("/register/cliente")
    public ResponseEntity<UserConfirmationDTO> register(@RequestBody RegisterDto registerDto) {
        UserConfirmationDTO confirmation = userService.registerWithConfirmation(registerDto);
        return new ResponseEntity<>(confirmation, HttpStatus.CREATED);
    }

    // Endpoint para registrar nuevos empleados con confirmación
    @PostMapping("/register/employee")
    public ResponseEntity<UserConfirmationDTO> registerEmployee(@RequestBody RegisterDto registerDto) {
        UserConfirmationDTO confirmation = userService.registerEmployeeWithConfirmation(registerDto);
        return new ResponseEntity<>(confirmation, HttpStatus.CREATED);
    }

    // Endpoint para renovar el token JWT
    @PostMapping("/refresh-token")
    public ResponseEntity<?> refreshToke(Authentication authentication){
        String token = jwtGenerator.refreshToken(authentication);
        JwtResponseDto jwtRefresh = new JwtResponseDto(token);
        return new ResponseEntity<JwtResponseDto>(jwtRefresh, HttpStatus.OK);
    }

    // Endpoint para obtener información del usuario autenticado
    @GetMapping("/logued")
    public ResponseEntity<UserDTO> getLoguedUser(@RequestHeader HttpHeaders headers){
        return new ResponseEntity<>(userService.getLoguedUser(headers), HttpStatus.OK);
    }
    
    // Endpoint para actualizar información de cliente y generar alerta
    @PutMapping("/update/client")
    public ResponseEntity<AlertDTO> updateClient(@RequestBody UserDTO userDTO) {
        AlertDTO alert = userService.updateClient(userDTO);
        return new ResponseEntity<>(alert, alert != null ? HttpStatus.OK : HttpStatus.NO_CONTENT);
    }

    // Endpoint para actualizar información de empleado y generar alerta
    @PutMapping("/update/employee")
    public ResponseEntity<AlertDTO> updateEmployee(@RequestBody UserDTO userDTO) {
        AlertDTO alert = userService.updateEmployee(userDTO);
        return new ResponseEntity<>(alert, alert != null ? HttpStatus.OK : HttpStatus.NO_CONTENT);
    }

    // Endpoint para obtener alertas de un usuario específico
    @GetMapping("/alerts/user/{userId}")
    public ResponseEntity<List<AlertDTO>> getUserAlerts(@PathVariable Long userId) {
        List<AlertDTO> alerts = userService.getUserAlerts(userId);
        return new ResponseEntity<>(alerts, HttpStatus.OK);
    }

    // Endpoint para obtener todas las alertas del sistema
    @GetMapping("/alerts")
    public ResponseEntity<List<AlertDTO>> getAllAlerts() {
        List<AlertDTO> alerts = userService.getAllAlerts();
        return new ResponseEntity<>(alerts, HttpStatus.OK);
    }

    // Endpoint para marcar una alerta como leída
    @PutMapping("/alerts/{alertId}/read")
    public ResponseEntity<Void> markAlertAsRead(@PathVariable Long alertId) {
        userService.markAlertAsRead(alertId);
        return new ResponseEntity<>(HttpStatus.OK);
    }
}
