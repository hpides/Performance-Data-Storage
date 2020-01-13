package de.hpi.tdgt.users;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import lombok.val;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;

@Controller
@RequestMapping("/users")
//Spring Magic, initializes UserRepo with an actual UserRepo
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
@Log4j2
public class UserController {

    private final UserRepository userRepository;

    @PostMapping(path="/new")
    public ResponseEntity<User> addNewUser (@RequestBody User user) {
        if(!userRepository.findById(user.getUsername()).isPresent()) {
            log.info("Added user "+ user.getUsername());
            userRepository.save(user);
            return new ResponseEntity<>(user, HttpStatus.OK);
        }
        log.error("User "+ user.getUsername()+" already exists!");
        return new ResponseEntity<>((User) null, HttpStatus.FORBIDDEN);
    }

    @PutMapping(path="/update")
    public ResponseEntity<User> updateUser (@RequestParam String username, @RequestParam String password, Principal principal) {
        if(principal.getName().equals(username)){
            User n = new User();
            n.setUsername(username);
            n.setPassword(password);
            userRepository.save(n);
            return new ResponseEntity<>(n, HttpStatus.OK);
        }
        return new ResponseEntity<>((User) null, HttpStatus.FORBIDDEN);
    }

    @GetMapping(path="/verify")
    public @ResponseBody
    User getCurrentUser (Principal principal) {
        return userRepository.findById(principal.getName()).orElse(null);
    }

    @DeleteMapping(path="/delete")
    public ResponseEntity<User> deleteUser (Principal principal) {
        val user = userRepository.findById(principal.getName());
        if(user.isPresent()){
            User userRepr = user.get();
            userRepository.delete(userRepr);
            log.info("user "+principal.getName()+" deleted!");
            return new ResponseEntity<>((User) null, HttpStatus.OK);
        }
        return new ResponseEntity<>((User) null, HttpStatus.FORBIDDEN);
    }

    @GetMapping(path="/all")
    public @ResponseBody Iterable<User> getAllUsers() {
        // This returns a JSON or XML with the users
        return userRepository.findAll();
    }
}
